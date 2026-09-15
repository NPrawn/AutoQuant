"""python -m contest check | build"""

import argparse
import json
import os
import random
import sys
from dataclasses import replace

from contest.blocks import build_pool, choose_window, format_time, label_weeks, load_source, transition_matrix
from contest.checks import check_bars, feasibility, stage2
from contest.sampling import KINDS, Params, evaluation_returns, max_factor_period, sample_sequence
from contest.stitch import aggregate, fill_gaps, stitch, tick_table, write_load_csv

BLOCK_START = {"MON": 0, "THU": 3}


def read_universe(path: str) -> list[str]:
    with open(path, encoding="utf-8") as file:
        return [line.strip() for line in file if line.strip() and not line.startswith("#")]


def _params(args: argparse.Namespace) -> Params:
    return Params(block_start=BLOCK_START[args.block_start], block_days=args.block_days,
                  warmup_weeks=args.warmup_weeks, eval_weeks=args.eval_weeks, candidates=args.candidates)


def _load(args: argparse.Namespace):
    universe = read_universe(args.universe)
    series = load_source(args.source, set(universe))
    missing = [asset for asset in universe if asset not in series]
    if missing:
        raise SystemExit(f"universe assets missing from source: {missing}")
    return series, universe


def _select(args: argparse.Namespace):
    """Pick the random source window (ADR-0011 B) and the universe that trades through it."""
    params = _params(args)
    series, candidates = _load(args)
    if not args.window_weeks:
        return params, series, candidates
    try:
        window, universe = choose_window(series, candidates, params.block_start, params.block_days,
                                         args.window_weeks, args.min_assets, random.Random(args.seed))
    except ValueError as error:
        raise SystemExit(str(error)) from error
    return replace(params, window=window), series, universe


def _window_json(params: Params) -> dict | None:
    return None if params.window is None else {"start": format_time(params.window[0]),
                                               "end": format_time(params.window[1])}


def _check(args: argparse.Namespace) -> int:
    params, series, universe = _select(args)
    report = {
        "window": _window_json(params),
        "universe": universe,
        "stage1": feasibility(series, universe, params, args.seed, args.min_weeks),
        "stage2": stage2(series, universe, params, args.seed, args.generated, args.seam_datasets),
    }
    report["passed"] = report["stage1"]["passed"] and report["stage2"]["passed"]
    text = json.dumps(report, ensure_ascii=False, indent=2)
    if args.out:
        os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
        with open(args.out, "w", encoding="utf-8", newline="\n") as file:
            file.write(text + "\n")
    print(text)
    return 0 if report["passed"] else 1


def _build(args: argparse.Namespace) -> int:
    params, series, universe = _select(args)
    pool = build_pool(series, universe, params.block_start, params.block_days, params.window)
    weeks = label_weeks(series, universe, pool, params.span)
    matrix = transition_matrix(weeks, params.span)
    ticks = tick_table(series)
    rng = random.Random(args.seed)
    period_start = params.virtual_start + params.warmup_weeks * params.span
    summary = {
        "seed": args.seed,
        "window": _window_json(params),
        "universe": universe,
        "markets": {asset: series[asset].market for asset in universe},
        "period_start": format_time(period_start),
        "period_end": format_time(period_start + params.eval_weeks * params.span),
        "max_factor_period": max_factor_period(params),
        "datasets": {},
    }
    os.makedirs(args.out, exist_ok=True)
    for kind in KINDS:
        sequence = sample_sequence(kind, weeks, matrix, series, universe, params, rng)
        hourly = stitch(series, universe, sequence, params.span, params.volume_ref_weeks, params.virtual_start, ticks)
        filled = 0
        if args.fill_gaps:
            hourly, filled = fill_gaps(hourly)
        bars = hourly + aggregate(hourly, 240) + aggregate(hourly, 1440)
        problems = check_bars(bars, ticks)
        if problems:
            raise SystemExit(f"{kind}: {len(problems)} bar check failures, first: {problems[:5]}")
        write_load_csv(os.path.join(args.out, f"{kind}.csv"), bars)
        held, rebalanced = evaluation_returns(sequence, series, universe, params)
        summary["datasets"][kind] = {
            "evaluation_return": held,
            "rebalanced_return": rebalanced,
            "filled_hours": filled,
            "weeks": [{"source_start": format_time(week.start), "label": week.label} for week in sequence],
        }
    with open(os.path.join(args.out, "weeks.json"), "w", encoding="utf-8", newline="\n") as file:
        json.dump(summary, file, ensure_ascii=False, indent=2)
        file.write("\n")
    keys = ("seed", "window", "period_start", "period_end", "max_factor_period")
    print(json.dumps({key: summary[key] for key in keys}, ensure_ascii=False))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="python -m contest")
    commands = parser.add_subparsers(dest="command", required=True)
    helps = {"check": "ADR-0012 1·2단계 확인 결과를 JSON으로 출력한다",
             "build": "대회 데이터 4벌을 로컬 DB 적재용 CSV로 만든다"}
    for name, help_text in helps.items():
        command = commands.add_parser(name, help=help_text)
        command.add_argument("--source", required=True, help="1시간봉 원본 CSV(.gz 가능)")
        command.add_argument("--universe", required=True, help="asset_id를 한 줄에 하나씩 적은 파일")
        command.add_argument("--seed", type=int, required=True)
        command.add_argument("--block-start", choices=sorted(BLOCK_START), default="MON")
        command.add_argument("--block-days", type=int, default=7)
        command.add_argument("--warmup-weeks", type=int, default=5)
        command.add_argument("--eval-weeks", type=int, default=13)
        command.add_argument("--candidates", type=int, default=Params().candidates)
        command.add_argument("--window-weeks", type=int, default=104,
                             help="과거에서 랜덤하게 고를 구간 길이(주). 0이면 전체 기간을 쓴다")
        command.add_argument("--min-assets", type=int, default=10,
                             help="구간 내내 거래된 종목이 이 수 이상인 구간만 고른다")
    commands.choices["check"].add_argument("--min-weeks", type=int, default=30)
    commands.choices["check"].add_argument("--generated", type=int, default=200)
    commands.choices["check"].add_argument("--seam-datasets", type=int, default=50)
    commands.choices["check"].add_argument("--out", help="결과 JSON을 저장할 경로")
    commands.choices["build"].add_argument("--out", required=True, help="CSV 4개와 weeks.json을 쓸 디렉터리")
    commands.choices["build"].add_argument(
        "--fill-gaps", action="store_true",
        help="거래 없는 시간을 직전 종가·거래량 0 봉으로 채운다. 엔진이 빈 시간을 처리하기 전까지 쓰는 임시 옵션",
    )

    args = parser.parse_args(argv)
    return {"check": _check, "build": _build}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
