"""ADR-0012 stages 3 and 4 on the local backend.

    python -m scripts.contest_sim.validate prepare --source S --universe U --seed N --out D
    python -m scripts.contest_sim.validate run --config .contest/sim.json --data D
    python -m scripts.contest_sim.validate report --data D --out O

Local development only, like run_contest: `run` replaces every candle in a contest-only database.
"""

import argparse
import json
import os
import random
import sys
from dataclasses import replace

from app.core.schema import Ranking, Rebalance, RebalancePeriod, Selection, Strategy
from contest.__main__ import build_bars, read_universe
from contest.blocks import (
    build_pool, choose_window, format_time, label_weeks, load_source, real_windows, transition_matrix,
)
from contest.sampling import Params, max_factor_period, sample_general
from contest.stitch import tick_table, write_load_csv
from scripts.contest_sim.fairness import stage3, stage4
from scripts.contest_sim.run_contest import (
    connect, create_strategies, load_candles, load_strategies, read_config, run_backtests,
)

REAL, GENERATED, THURSDAY = "real", "gen", "thu"
PER_GROUP = 5
THURSDAY_START = 3
GRID_BASE = "momentum-7d"
GRID_PERIODS = (24, 72, 168, 336, 504, 720)
GRID_TOPS = (3, 5)
GRID_REBALANCES = (RebalancePeriod.DAILY, RebalancePeriod.WEEKLY)


def grid_keys() -> dict[str, tuple[int, int, str]]:
    return {f"momentum-{period}h-top{top}-{rebalance.value.lower()}": (period, top, rebalance.value)
            for period in GRID_PERIODS for top in GRID_TOPS for rebalance in GRID_REBALANCES}


def grid_strategies(contest: list[tuple[str, str, Strategy]]) -> list[tuple[str, str, Strategy]]:
    """Variants of the contest's momentum-7d strategy (1H, same filter): return period x top N x rebalance."""
    base = next(strategy for name, _, strategy in contest if name == GRID_BASE)
    return [
        (name, "3단계 모멘텀 격자", Strategy(
            timeframe=base.timeframe,
            filters=base.filters,
            ranking=Ranking(factor=base.ranking.factor, period=period, direction=base.ranking.direction),
            selection=Selection(topN=top),
            rebalance=Rebalance(period=RebalancePeriod(rebalance)),
        ))
        for name, (period, top, rebalance) in grid_keys().items()
    ]


def prepare(source: str, universe_path: str, seed: int, out: str, params: Params = Params(),
            window_weeks: int = 104, min_assets: int = 12) -> dict:
    """Five real runs, five Monday-cut and five Thursday-cut general markets from the seed's source window."""
    candidates = read_universe(universe_path)
    series = load_source(source, set(candidates))
    window, universe = choose_window(series, candidates, params.block_start, params.block_days, window_weeks,
                                     min_assets, random.Random(seed))
    ticks = tick_table(series)
    length = params.warmup_weeks + params.eval_weeks
    manifest = {
        "seed": seed,
        "window": {"start": format_time(window[0]), "end": format_time(window[1])},
        "universe": universe,
        "markets": {asset: series[asset].market for asset in universe},
        "max_factor_period": max_factor_period(params),
        "datasets": {},
    }
    os.makedirs(out, exist_ok=True)

    def write(name: str, group: str, dataset_params: Params, sequence) -> None:
        bars, filled = build_bars(series, universe, sequence, dataset_params, ticks, fill=True)
        write_load_csv(os.path.join(out, f"{name}.csv"), bars)
        start = dataset_params.virtual_start + dataset_params.warmup_weeks * dataset_params.span
        manifest["datasets"][name] = {
            "group": group,
            "period_start": format_time(start),
            "period_end": format_time(start + dataset_params.eval_weeks * dataset_params.span),
            "filled_hours": filled,
            "weeks": [format_time(week.start) for week in sequence],
        }

    for group, block_start in ((GENERATED, params.block_start), (THURSDAY, THURSDAY_START)):
        cut = replace(params, window=window, block_start=block_start)
        pool = build_pool(series, universe, cut.block_start, cut.block_days, window)
        weeks = label_weeks(series, universe, pool, cut.span)
        matrix = transition_matrix(weeks, cut.span)
        if group == GENERATED:
            real = real_windows(weeks, length, cut.span)
            if len(real) <= (PER_GROUP - 1) * length:
                raise SystemExit(f"window holds {len(real)} real {length}-week runs, "
                                 f"need {(PER_GROUP - 1) * length + 1}")
            for index in range(PER_GROUP):
                write(f"{REAL}-{index + 1}", REAL, cut, real[index * length])
        for index in range(PER_GROUP):
            write(f"{group}-{index + 1}", group, cut,
                  sample_general(weeks, matrix, length, random.Random(seed + index + 1)))
    with open(os.path.join(out, "manifest.json"), "w", encoding="utf-8", newline="\n") as file:
        json.dump(manifest, file, ensure_ascii=False, indent=2)
        file.write("\n")
    return manifest


def _read_manifest(data: str) -> dict:
    with open(os.path.join(data, "manifest.json"), encoding="utf-8") as file:
        return json.load(file)


def _read_results(data: str) -> dict[str, dict[str, dict]]:
    results: dict[str, dict[str, dict]] = {}
    path = os.path.join(data, "results.jsonl")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as file:
            for line in file:
                row = json.loads(line)
                results.setdefault(row["dataset"], {})[row["strategy"]] = row
    return results


def _expected(manifest: dict, contest_names: list[str]) -> dict[str, list[str]]:
    """The contest strategies run everywhere; the momentum grid runs on real and Monday-cut data."""
    grid = list(grid_keys())
    return {name: contest_names + ([] if dataset["group"] == THURSDAY else grid)
            for name, dataset in manifest["datasets"].items()}


def run(config: dict, data: str, strategies_path: str, poll_seconds: float = 5) -> None:
    """Backtest every dataset in manifest order. Results already in results.jsonl are skipped, so a stopped run
    resumes where it left off."""
    manifest = _read_manifest(data)
    contest = load_strategies(strategies_path, manifest["max_factor_period"])
    api = connect(config)
    ids_path = os.path.join(data, "strategy_ids.json")
    if os.path.exists(ids_path):
        with open(ids_path, encoding="utf-8") as file:
            strategy_ids = json.load(file)
    else:
        strategy_ids = create_strategies(api, contest + grid_strategies(contest))
        with open(ids_path, "w", encoding="utf-8", newline="\n") as file:
            json.dump(strategy_ids, file, indent=2)
            file.write("\n")
    done = _read_results(data)
    expected = _expected(manifest, [name for name, _, _ in contest])
    for name, dataset in manifest["datasets"].items():
        todo = {strategy: strategy_ids[strategy] for strategy in expected[name]
                if strategy not in done.get(name, {})}
        if not todo:
            continue
        load_candles(config, os.path.join(data, f"{name}.csv"))
        runs = run_backtests(api, todo, dataset["period_start"], dataset["period_end"],
                             f"{name}-seed{manifest['seed']}", poll_seconds)
        with open(os.path.join(data, "results.jsonl"), "a", encoding="utf-8", newline="\n") as file:
            for strategy, result in runs.items():
                file.write(json.dumps({"dataset": name, "strategy": strategy, **result}, ensure_ascii=False) + "\n")


def report(data: str, strategies_path: str, out: str) -> dict:
    manifest = _read_manifest(data)
    contest_names = [name for name, _, _ in load_strategies(strategies_path, manifest["max_factor_period"])]
    results = _read_results(data)
    missing = [(name, strategy) for name, strategies in _expected(manifest, contest_names).items()
               for strategy in strategies if strategy not in results.get(name, {})]
    if missing:
        raise SystemExit(f"{len(missing)} backtests missing, first: {missing[:3]}")
    groups = {group: [name for name, dataset in manifest["datasets"].items() if dataset["group"] == group]
              for group in (REAL, GENERATED, THURSDAY)}
    verdict = {
        "seed": manifest["seed"],
        "window": manifest["window"],
        "stage3": stage3(results, groups[REAL], groups[GENERATED], grid_keys()),
        "stage4": stage4(results, groups[REAL], groups[GENERATED], groups[THURSDAY], contest_names),
    }
    verdict["passed"] = verdict["stage3"]["passed"] and verdict["stage4"]["passed"]
    text = json.dumps(verdict, ensure_ascii=False, indent=2)
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    with open(out, "w", encoding="utf-8", newline="\n") as file:
        file.write(text + "\n")
    print(text)
    return verdict


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m scripts.contest_sim.validate")
    commands = parser.add_subparsers(dest="command", required=True)
    prepare_parser = commands.add_parser("prepare", help="실제 5구간·월요일 일반장 5벌·목요일 일반장 5벌 CSV를 만든다")
    prepare_parser.add_argument("--source", required=True)
    prepare_parser.add_argument("--universe", required=True)
    prepare_parser.add_argument("--seed", type=int, required=True)
    prepare_parser.add_argument("--out", required=True)
    prepare_parser.add_argument("--window-weeks", type=int, default=104)
    prepare_parser.add_argument("--min-assets", type=int, default=12)
    run_parser = commands.add_parser("run", help="데이터셋마다 캔들을 바꿔 넣고 백테스트한다. 이미 있는 결과는 건너뛴다")
    run_parser.add_argument("--config", required=True)
    run_parser.add_argument("--data", required=True)
    run_parser.add_argument("--strategies", default="scripts/contest_sim/strategies.json")
    report_parser = commands.add_parser("report", help="3·4단계 판정 JSON을 만든다")
    report_parser.add_argument("--data", required=True)
    report_parser.add_argument("--strategies", default="scripts/contest_sim/strategies.json")
    report_parser.add_argument("--out", required=True)
    args = parser.parse_args(argv)
    if args.command == "prepare":
        prepare(args.source, args.universe, args.seed, args.out, Params(), args.window_weeks, args.min_assets)
    elif args.command == "run":
        run(read_config(args.config), args.data, args.strategies)
    else:
        report(args.data, args.strategies, args.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
