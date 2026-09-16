"""Run one local contest against the local backend.

    python -m scripts.contest_sim.run_contest seed --config .contest/sim.json --datasets D
    python -m scripts.contest_sim.run_contest run --config .contest/sim.json --datasets D --strategies S --out O

Local development only: HTTP to the local Spring API, `docker exec psql` to a contest-only database in the local
Postgres container (`run` replaces every candle in it). Settings come from a JSON file, never from environment
variables (ai/CLAUDE.md).
"""

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

from app.core.schema import Condition, FactorOperand, FilterGroup, Strategy
from contest.sampling import KINDS
from scripts.contest_sim.jwt_token import issue
from scripts.contest_sim.scoring import score

LOAD_SQL = (
    "\\copy market_data.minute_candle (asset_id, interval_minutes, open_time, open_price, high_price, low_price, "
    "close_price, trade_volume, trade_amount, last_event_time) FROM STDIN WITH (FORMAT csv, HEADER true)"
)


def load_strategies(path: str, caps: dict[str, int]) -> list[tuple[str, str, Strategy]]:
    """Validate every DSL with the schema and reject factor periods longer than the warmup (ADR-0013)."""
    with open(path, encoding="utf-8") as file:
        items = json.load(file)
    strategies = []
    for item in items:
        strategy = Strategy.model_validate(item["dsl"])
        periods = [strategy.ranking.period]
        nodes = [strategy.filters]
        while nodes:
            node = nodes.pop()
            if isinstance(node, FilterGroup):
                nodes.extend(node.children)
            elif isinstance(node, Condition):
                periods += [side.period for side in (node.left, node.right) if isinstance(side, FactorOperand)]
        cap = caps[strategy.timeframe.value]
        if max(periods) > cap:
            raise ValueError(f"{item['name']}: factor period {max(periods)} exceeds {cap} for {strategy.timeframe.value}")
        strategies.append((item["name"], item.get("description", ""), strategy))
    return strategies


def _psql(config: dict, args: list[str], stdin: bytes) -> None:
    command = ["docker", "exec", "-i", config["postgres_container"], "psql", "-v", "ON_ERROR_STOP=1",
               "-U", config["db_user"], "-d", config["db_name"], *args]
    subprocess.run(command, input=stdin, check=True)


def _literal(value: str) -> str:
    return "NULL" if value == "" else "'" + value.replace("'", "''") + "'"


def seed(config: dict, datasets_dir: str) -> None:
    """Insert the test member and the dataset's assets, listed as trading (the engine only picks listed assets)."""
    with open(os.path.join(datasets_dir, "weeks.json"), encoding="utf-8") as file:
        markets = json.load(file)["markets"]
    statements = [
        "INSERT INTO account.member (member_id, login_provider, login_provider_member_id, email_address, "
        f"display_name, member_status) VALUES ({_literal(config['member_id'])}, 'SSAFY', 'local-contest', "
        "'contest@local.test', 'local contest', 'ACTIVE') ON CONFLICT (member_id) DO NOTHING;"
    ]
    for asset_id, market in markets.items():
        quote, base = market.split("-", 1)
        statements.append(
            "INSERT INTO reference.asset (asset_id, exchange, exchange_market_code, base_currency_code, "
            "quote_currency_code, korean_name, english_name, trading_status) VALUES "
            f"({_literal(asset_id)}, 'UPBIT', {_literal(market)}, {_literal(base)}, {_literal(quote)}, "
            f"{_literal(base)}, {_literal(base)}, 'TRADING') ON CONFLICT DO NOTHING;"
        )
    _psql(config, [], "\n".join(statements).encode("utf-8"))
    print(f"seeded member and {len(markets)} assets")


class Api:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip("/")
        self.token = token

    def call(self, method: str, path: str, body: dict | None = None) -> dict:
        data = None if body is None else json.dumps(body).encode("utf-8")
        request = urllib.request.Request(self.base_url + path, data=data, method=method, headers={
            "Authorization": f"Bearer {self.token}", "Content-Type": "application/json",
        })
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.loads(response.read() or b"null")
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"{method} {path} -> {error.code}: {error.read().decode('utf-8', 'replace')}") from error


def _collect(api: Api, backtest_id: str, poll_seconds: float) -> dict:
    while True:
        run = api.call("GET", f"/backtests/{backtest_id}")
        if run["status"] in ("COMPLETED", "FAILED"):
            break
        time.sleep(poll_seconds)
    if run["status"] == "FAILED":
        return {"status": "FAILED", "failure": run.get("failureCode"), "return": None, "mdd": None, "exposure": None}
    result = api.call("GET", f"/backtests/{backtest_id}/result")
    exposures, page = [], 0
    while True:
        equity = api.call("GET", f"/backtests/{backtest_id}/equity?page={page}&size=100")
        exposures += [1 - float(point["cashBalance"]) / float(point["totalAssetValue"])
                      for point in equity["content"] if float(point["totalAssetValue"]) > 0]
        page += 1
        if page >= equity["totalPages"]:
            break
    return {"status": "COMPLETED", "return": float(result["cumulativeReturn"]), "mdd": float(result["mdd"]),
            "exposure": sum(exposures) / len(exposures) if exposures else 0.0, "trades": result["tradeCount"]}


def read_config(path: str) -> dict:
    """Local settings. Refuse any database that is not contest-only: loading candles replaces every candle."""
    with open(path, encoding="utf-8") as file:
        config = json.load(file)
    if "contest" not in config["db_name"]:
        raise SystemExit(f"db_name {config['db_name']!r} must name a contest-only database: run replaces every candle")
    return config


def connect(config: dict) -> Api:
    return Api(config["api"], issue(config["member_id"], config["jwt_secret"], config.get("jwt_issuer", "a107")))


def load_candles(config: dict, csv_path: str) -> None:
    _psql(config, ["-c", "DELETE FROM market_data.minute_candle"], b"")
    with open(csv_path, "rb") as file:
        _psql(config, ["-c", LOAD_SQL], file.read())


def create_strategies(api: Api, strategies: list[tuple[str, str, Strategy]]) -> dict[str, str]:
    return {
        name: api.call("POST", "/strategies", {
            "name": name, "description": description, "strategyDsl": strategy.model_dump(mode="json"),
        })["strategyId"]
        for name, description, strategy in strategies
    }


def run_backtests(api: Api, strategy_ids: dict[str, str], period_start: str, period_end: str, label: str,
                  poll_seconds: float = 5) -> dict[str, dict]:
    """Submit one backtest per strategy on the loaded candles, then wait for each result."""
    backtests = {
        name: api.call("POST", "/backtests", {
            "strategyId": strategy_id, "periodStart": period_start, "periodEnd": period_end,
            "quoteCurrency": "KRW", "initialCapital": 10_000_000, "feeRate": 0.0005, "slippageBps": 10,
            "executionPriceRule": "NEXT_CANDLE_OPEN", "engineVersion": "local-sim", "marketDataVersion": label,
        })["backtestId"]
        for name, strategy_id in strategy_ids.items()
    }
    results = {}
    for name, backtest_id in backtests.items():
        results[name] = _collect(api, backtest_id, poll_seconds)
        print(label, name, results[name], flush=True)
    return results


def run(config: dict, datasets_dir: str, strategies_path: str, out_path: str, poll_seconds: float = 5) -> dict:
    with open(os.path.join(datasets_dir, "weeks.json"), encoding="utf-8") as file:
        summary = json.load(file)
    strategies = load_strategies(strategies_path, summary["max_factor_period"])
    api = connect(config)
    strategy_ids = create_strategies(api, strategies)
    results: dict[str, dict[str, dict]] = {name: {} for name in strategy_ids}
    for kind in KINDS:
        load_candles(config, os.path.join(datasets_dir, f"{kind}.csv"))
        runs = run_backtests(api, strategy_ids, summary["period_start"], summary["period_end"],
                             f"{kind}-seed{summary['seed']}", poll_seconds)
        for name, result in runs.items():
            results[name][kind] = result
    report ={"seed": summary["seed"], "window": summary.get("window"), "results": results, "scores": score(results)}
    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as file:
        json.dump(report, file, ensure_ascii=False, indent=2)
        file.write("\n")
    for row in report["scores"]:
        if row["disqualified"]:
            print(f"{row['strategy']}: 실격 - {row['disqualified']}")
        else:
            print(f"{row['strategy']}: 총점 {row['total']:.1f} (수익률 {row['return_score']:.1f}, "
                  f"안정성 {row['stability_score']:.1f})")
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m scripts.contest_sim.run_contest")
    commands = parser.add_subparsers(dest="command", required=True)
    seed_parser = commands.add_parser("seed", help="대회 전용 DB에 테스트 회원과 데이터셋 종목을 넣는다")
    seed_parser.add_argument("--config", required=True)
    seed_parser.add_argument("--datasets", required=True, help="python -m contest build 출력 디렉터리")
    run_parser = commands.add_parser("run", help="데이터셋 4벌에 전략을 모두 백테스트하고 채점한다")
    run_parser.add_argument("--config", required=True)
    run_parser.add_argument("--datasets", required=True)
    run_parser.add_argument("--strategies", required=True)
    run_parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    config = read_config(args.config)
    if args.command == "seed":
        seed(config, args.datasets)
    else:
        run(config, args.datasets, args.strategies, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
