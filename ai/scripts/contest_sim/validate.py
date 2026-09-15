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
            window_weeks: int = 104, min_assets: int = 10) -> dict:
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


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m scripts.contest_sim.validate")
    commands = parser.add_subparsers(dest="command", required=True)
    prepare_parser = commands.add_parser("prepare", help="실제 5구간·월요일 일반장 5벌·목요일 일반장 5벌 CSV를 만든다")
    prepare_parser.add_argument("--source", required=True)
    prepare_parser.add_argument("--universe", required=True)
    prepare_parser.add_argument("--seed", type=int, required=True)
    prepare_parser.add_argument("--out", required=True)
    prepare_parser.add_argument("--window-weeks", type=int, default=104)
    prepare_parser.add_argument("--min-assets", type=int, default=10)
    args = parser.parse_args(argv)
    prepare(args.source, args.universe, args.seed, args.out, Params(), args.window_weeks, args.min_assets)
    return 0


if __name__ == "__main__":
    sys.exit(main())
