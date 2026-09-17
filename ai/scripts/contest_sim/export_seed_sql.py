"""Turn a generator output directory into one Flyway migration that seeds a contest round.

    python -m scripts.contest_sim.export_seed_sql --datasets D --round-id U --out V21__seed_contest_round.sql \
        --title "1회차" --submission-starts 2026-09-16T00:00:00Z --submission-ends 2026-10-16T00:00:00Z

Assets are joined by market code so the same file works on any database whose reference.asset holds them
(ADR-0016). Only hourly candles are written; 4h and daily rows are aggregated inside the migration with the
ADR-0005 rollup rule.
"""

import argparse
import csv
import json
import os
import sys
import uuid

from scripts.contest_sim.scoring import market_drawdowns

KINDS = ("GENERAL", "BULL", "BEAR", "SIDEWAYS")
CHUNK_ROWS = 500
HOUR_MINUTES = 60


def _quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def _number(value: str) -> str:
    """Drop the padding zeros the load CSV carries; the column type keeps the scale."""
    if "." not in value:
        return value
    trimmed = value.rstrip("0").rstrip(".")
    return trimmed or "0"


def _dataset_ids(round_id: str) -> dict[str, str]:
    """Same round and generator output always name the same datasets, so a rerun writes the same file."""
    return {kind: str(uuid.uuid5(uuid.UUID(round_id), kind)) for kind in KINDS}


def _warmup_weeks(summary: dict) -> int:
    """워밍업 주 = 뽑은 주 − 평가 주. 팩터 기간 상한이 여기서 나온다 (ADR-0013)."""
    return len(summary["datasets"]["GENERAL"]["weeks"]) - summary_evaluation_weeks(summary)


def _round_sql(round_id: str, title: str, starts_at: str, ends_at: str, summary: dict, max_submissions: int) -> str:
    warmup_weeks = _warmup_weeks(summary)
    return (
        "INSERT INTO contest.contest_round (\n"
        "    round_id, title, submission_starts_at, submission_ends_at, phase,\n"
        "    max_submissions, warmup_weeks, evaluation_weeks\n"
        ") VALUES (\n"
        f"    {_quote(round_id)}, {_quote(title)}, {_quote(starts_at)}, {_quote(ends_at)}, 'READY',\n"
        f"    {max_submissions}, {warmup_weeks}, {summary_evaluation_weeks(summary)}\n"
        ");\n"
    )


def summary_evaluation_weeks(summary: dict) -> int:
    """Evaluation weeks = period length / one week, taken from the generated period."""
    return round((_epoch(summary["period_end"]) - _epoch(summary["period_start"])) / (7 * 24 * 3600))


def _epoch(text: str) -> int:
    from datetime import datetime

    return int(datetime.fromisoformat(text.replace("Z", "+00:00")).timestamp())


def _dataset_sql(round_id: str, dataset_ids: dict[str, str], summary: dict, generator_version: str,
                 drawdowns: dict[str, dict[str, float]]) -> str:
    """drawdowns[kind][timeframe] 는 국면 점수의 기준인 시장 낙폭이다 (ADR-0003)."""
    rows = []
    for kind in KINDS:
        dataset = summary["datasets"][kind]
        weeks = json.dumps(dataset["weeks"], ensure_ascii=False)
        market = drawdowns[kind]
        rows.append(
            f"    ({_quote(dataset_ids[kind])}, {_quote(round_id)}, {_quote(kind)}, "
            f"{_quote(summary['period_start'])}, {_quote(summary['period_end'])}, "
            f"{dataset['evaluation_return']:.8f}, {market['1H']:.8f}, {market['4H']:.8f}, {market['1D']:.8f}, "
            f"{_quote(summary['window']['start'])}, "
            f"{_quote(summary['window']['end'])}, {summary['seed']}, {_quote(weeks)}, "
            f"{_quote(generator_version)}, {dataset['filled_hours']})"
        )
    return (
        "INSERT INTO contest.market_dataset (\n"
        "    dataset_id, round_id, kind, period_start, period_end, evaluation_return,\n"
        "    market_mdd_1h, market_mdd_4h, market_mdd_1d,\n"
        "    source_window_start, source_window_end, seed, source_weeks, generator_version, filled_hours\n"
        ") VALUES\n" + ",\n".join(rows) + ";\n"
    )


def _asset_seed_sql(markets: list[str]) -> str:
    """빈 DB에도 대회 종목이 있어야 데이터셋을 붙일 수 있다. 이미 있으면 그대로 두고 수집 대상으로 등록하지는 않는다."""
    rows = []
    for market in markets:
        quote, base = market.split("-", 1)
        asset_id = uuid.uuid5(uuid.NAMESPACE_URL, f"upbit:{market}")
        rows.append(
            f"    ({_quote(str(asset_id))}, 'UPBIT', {_quote(market)}, {_quote(base)}, {_quote(quote)},"
            f" {_quote(base)}, {_quote(base)}, 'TRADING')"
        )
    return (
        "INSERT INTO reference.asset (\n"
        "    asset_id, exchange, exchange_market_code, base_currency_code, quote_currency_code,\n"
        "    korean_name, english_name, trading_status\n"
        ") VALUES\n" + ",\n".join(rows) + "\n"
        "ON CONFLICT (exchange, exchange_market_code) DO NOTHING;\n"
    )


def _asset_sql(round_id: str, markets: list[str]) -> str:
    codes = ", ".join(_quote(market) for market in sorted(markets))
    return (
        "INSERT INTO contest.market_dataset_asset (dataset_id, asset_id)\n"
        "SELECT dataset.dataset_id, asset.asset_id\n"
        "FROM contest.market_dataset AS dataset\n"
        "JOIN reference.asset AS asset\n"
        "    ON asset.exchange = 'UPBIT'\n"
        f"    AND asset.exchange_market_code IN ({codes})\n"
        f"WHERE dataset.round_id = {_quote(round_id)};\n\n"
        "DO $$\n"
        "BEGIN\n"
        "    IF (\n"
        "        SELECT count(*)\n"
        "        FROM contest.market_dataset_asset AS dataset_asset\n"
        "        JOIN contest.market_dataset AS dataset ON dataset.dataset_id = dataset_asset.dataset_id\n"
        f"        WHERE dataset.round_id = {_quote(round_id)}\n"
        f"    ) <> {len(markets) * len(KINDS)} THEN\n"
        "        RAISE EXCEPTION '대회 종목이 reference.asset 에 모두 있어야 한다: % 개 필요',"
        f" {len(markets) * len(KINDS)};\n"
        "    END IF;\n"
        "END $$;\n"
    )


def _hourly_sql(dataset_id: str, rows: list[tuple[str, str, str, str, str, str, str, str]]) -> list[str]:
    statements = []
    for start in range(0, len(rows), CHUNK_ROWS):
        chunk = rows[start:start + CHUNK_ROWS]
        values = ",\n".join(
            f"        ({_quote(market)}, {_quote(open_time)}, {open_price}, {high_price}, {low_price},"
            f" {close_price}, {volume}, {amount})"
            for market, open_time, open_price, high_price, low_price, close_price, volume, amount in chunk
        )
        statements.append(
            "INSERT INTO contest.market_candle (\n"
            "    dataset_id, asset_id, interval_minutes, open_time,\n"
            "    open_price, high_price, low_price, close_price, trade_volume, trade_amount\n"
            ")\n"
            f"SELECT {_quote(dataset_id)}, asset.asset_id, {HOUR_MINUTES}, candle.open_time::timestamptz,\n"
            "       candle.open_price::numeric, candle.high_price::numeric, candle.low_price::numeric,\n"
            "       candle.close_price::numeric, candle.trade_volume::numeric, candle.trade_amount::numeric\n"
            "FROM (VALUES\n" + values + "\n"
            ") AS candle(market_code, open_time, open_price, high_price, low_price, close_price,"
            " trade_volume, trade_amount)\n"
            "JOIN reference.asset AS asset\n"
            "    ON asset.exchange = 'UPBIT' AND asset.exchange_market_code = candle.market_code;\n"
        )
    return statements


def _rollup_sql(round_id: str, interval_minutes: int) -> str:
    """4h and daily rows follow the Go rollup rule: UTC buckets, first open, last close, max/min, sums (ADR-0005)."""
    bucket_seconds = interval_minutes * 60
    return (
        "INSERT INTO contest.market_candle (\n"
        "    dataset_id, asset_id, interval_minutes, open_time,\n"
        "    open_price, high_price, low_price, close_price, trade_volume, trade_amount\n"
        ")\n"
        "SELECT hourly.dataset_id,\n"
        "       hourly.asset_id,\n"
        f"       {interval_minutes},\n"
        f"       to_timestamp(floor(extract(epoch FROM hourly.open_time) / {bucket_seconds}) * {bucket_seconds}),\n"
        "       (array_agg(hourly.open_price ORDER BY hourly.open_time))[1],\n"
        "       max(hourly.high_price),\n"
        "       min(hourly.low_price),\n"
        "       (array_agg(hourly.close_price ORDER BY hourly.open_time DESC))[1],\n"
        "       sum(hourly.trade_volume),\n"
        "       sum(hourly.trade_amount)\n"
        "FROM contest.market_candle AS hourly\n"
        "JOIN contest.market_dataset AS dataset ON dataset.dataset_id = hourly.dataset_id\n"
        f"WHERE dataset.round_id = {_quote(round_id)}\n"
        f"  AND hourly.interval_minutes = {HOUR_MINUTES}\n"
        "GROUP BY hourly.dataset_id,\n"
        "         hourly.asset_id,\n"
        f"         to_timestamp(floor(extract(epoch FROM hourly.open_time) / {bucket_seconds}) * {bucket_seconds});\n"
    )


def _read_hourly(path: str, markets: dict[str, str]) -> list[tuple]:
    rows = []
    with open(path, encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            if row["interval_minutes"] != str(HOUR_MINUTES):
                continue
            rows.append((
                markets[row["asset_id"]],
                row["open_time"],
                _number(row["open_price"]),
                _number(row["high_price"]),
                _number(row["low_price"]),
                _number(row["close_price"]),
                _number(row["trade_volume"]),
                _number(row["trade_amount"]),
            ))
    return rows


def _replace_sql(round_id: str) -> str:
    """같은 회차를 새 데이터로 바꾼다. 이미 적용한 마이그레이션은 고치지 않는다 (ADR-0016 결정 6).

    제출과 채점 결과, 대회 백테스트가 옛 데이터셋을 참조하므로 함께 지운다. 백테스트의 결과·체결·자산
    추이는 외래 키가 CASCADE 라 따라 지워진다.
    """
    quoted = _quote(round_id)
    entries = f"SELECT entry_id FROM contest.contest_entry WHERE round_id = {quoted}"
    datasets = f"SELECT dataset_id FROM contest.market_dataset WHERE round_id = {quoted}"

    return (
        "-- 같은 회차의 옛 데이터를 지운다. 순서는 외래 키를 따른다.\n"
        f"DELETE FROM contest.contest_result WHERE entry_id IN ({entries});\n"
        f"DELETE FROM contest.contest_evaluation WHERE entry_id IN ({entries});\n"
        f"DELETE FROM trading.backtest_run WHERE contest_dataset_id IN ({datasets});\n"
        f"DELETE FROM contest.contest_entry WHERE round_id = {quoted};\n"
        f"DELETE FROM contest.market_candle WHERE dataset_id IN ({datasets});\n"
        f"DELETE FROM contest.market_dataset_asset WHERE dataset_id IN ({datasets});\n"
        f"DELETE FROM contest.market_dataset WHERE round_id = {quoted};\n"
        f"DELETE FROM contest.contest_round WHERE round_id = {quoted};\n"
    )


def export(datasets_dir: str, round_id: str, title: str, starts_at: str, ends_at: str, max_submissions: int,
           generator_version: str, out_path: str, replace: bool = False) -> dict:
    with open(os.path.join(datasets_dir, "weeks.json"), encoding="utf-8") as file:
        summary = json.load(file)
    markets = summary["markets"]
    dataset_ids = _dataset_ids(round_id)

    parts = [
        f"-- 대회 {title} 데이터. 생성기 시드 {summary['seed']}, 구간 {summary['window']['start']}"
        f" ~ {summary['window']['end']} (ADR-0011, ADR-0014, ADR-0016).\n"
        "-- ai/scripts/contest_sim/export_seed_sql.py 가 만든 파일이다. 손으로 고치지 않는다.\n\n",
    ]
    if replace:
        parts.append(_replace_sql(round_id))
        parts.append("\n")
    parts += [
        _round_sql(round_id, title, starts_at, ends_at, summary, max_submissions),
        "\n",
        _dataset_sql(round_id, dataset_ids, summary, generator_version, {
            kind: market_drawdowns(os.path.join(datasets_dir, f"{kind}.csv"), summary["period_start"],
                                   summary["period_end"])
            for kind in KINDS
        }),
        "\n",
        _asset_seed_sql(sorted(set(markets.values()))),
        "\n",
        _asset_sql(round_id, sorted(set(markets.values()))),
        "\n",
    ]

    hourly_rows = 0
    for kind in KINDS:
        rows = _read_hourly(os.path.join(datasets_dir, f"{kind}.csv"), markets)
        hourly_rows += len(rows)
        parts.append(f"-- {kind} 1시간봉 {len(rows)}행\n")
        parts.extend(statement + "\n" for statement in _hourly_sql(dataset_ids[kind], rows))

    parts.append("-- 4시간봉과 일봉은 1시간봉을 집계해서 만든다 (ADR-0005).\n")
    parts.append(_rollup_sql(round_id, 240))
    parts.append("\n")
    parts.append(_rollup_sql(round_id, 1440))

    os.makedirs(os.path.dirname(out_path) or ".", exist_ok=True)
    with open(out_path, "w", encoding="utf-8", newline="\n") as file:
        file.write("".join(parts))

    return {"round_id": round_id, "datasets": dataset_ids, "hourly_rows": hourly_rows,
            "bytes": os.path.getsize(out_path), "markets": sorted(set(markets.values())),
            "replace": replace, "warmup_weeks": _warmup_weeks(summary)}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m scripts.contest_sim.export_seed_sql")
    parser.add_argument("--datasets", required=True, help="python -m contest build 출력 디렉터리")
    parser.add_argument("--round-id", required=True, help="회차 UUID. 데이터셋 ID는 여기서 파생한다")
    parser.add_argument("--title", required=True)
    parser.add_argument("--submission-starts", required=True, help="예: 2026-09-16T00:00:00Z")
    parser.add_argument("--submission-ends", required=True)
    parser.add_argument("--max-submissions", type=int, default=5)
    parser.add_argument("--generator-version", required=True, help="예: contest-2026-09-15")
    parser.add_argument("--out", required=True, help="Flyway 마이그레이션 경로")
    parser.add_argument("--replace", action="store_true",
                        help="같은 회차의 옛 데이터·제출·채점 결과를 지우고 새로 넣는다")
    args = parser.parse_args(argv)

    report = export(args.datasets, args.round_id, args.title, args.submission_starts, args.submission_ends,
                    args.max_submissions, args.generator_version, args.out, args.replace)
    print(json.dumps(report, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
