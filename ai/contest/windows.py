"""Pick real source windows for contest datasets (ADR-0018).

No week is moved. A dataset is one contiguous stretch of real candles: `warmup_weeks` to look back on and
`eval_weeks` to be scored on. The regimes are real stretches whose evaluation return lands in the target.
"""

import bisect
import math
import random
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

from contest.blocks import DAY, Series, first_week_start
from contest.sampling import GenerationError, KINDS, Params, in_target, rebalanced_return, stitched_return
from contest.checks import check_bars
from contest.stitch import AMOUNT_PLACES, VOLUME_PLACES, WIDE, Bar, to_decimal

PRICE_FLOOR = 1.0


@dataclass(frozen=True)
class Selection:
    kind: str
    start: int
    end: int
    universe: list[str]
    held: float
    rebalanced: float


Index = dict[str, tuple[int, int, list[int]]]   # asset -> (first candle, last candle, times under the floor)


def build_index(series: Series, candidates: list[str], floor: float = PRICE_FLOOR) -> Index:
    """Scan every candle once. Window checks then cost a bisect instead of a walk."""
    return {asset: (series[asset].times[0], series[asset].times[-1],
                    [candle.t for candle in series[asset].candles if candle.l < floor])
            for asset in candidates if asset in series}


def alive_in(index: Index, candidates: list[str], start: int, end: int) -> list[str]:
    found = []
    for asset in candidates:
        entry = index.get(asset)
        if entry is None:
            continue
        first, last, cheap = entry
        if first >= start or last < end:
            continue
        position = bisect.bisect_left(cheap, start)
        if position < len(cheap) and cheap[position] < end:
            continue
        found.append(asset)
    return found


def alive(series: Series, candidates: list[str], start: int, end: int, floor: float = PRICE_FLOOR) -> list[str]:
    return alive_in(build_index(series, candidates, floor), candidates, start, end)


def window_starts(series: Series, candidates: list[str], params: Params, minimum: int,
                  floor: float = PRICE_FLOOR, index: Index | None = None) -> list[int]:
    """Every aligned start whose window holds at least `minimum` tradable assets."""
    index = build_index(series, candidates, floor) if index is None else index
    span = params.span
    length = (params.warmup_weeks + params.eval_weeks) * span
    first = first_week_start(min(entry[0] for entry in index.values()), params.block_start)
    last = max(entry[1] for entry in index.values())
    starts = []
    while first + length <= last:
        if len(alive_in(index, candidates, first, first + length)) >= minimum:
            starts.append(first)
        first += span
    return starts


def evaluation_returns(series: Series, universe: list[str], start: int, params: Params) -> tuple[float, float]:
    """(buy-and-hold, daily-rebalanced) equal-weight returns of the evaluation stretch."""
    span = params.span
    evaluation = start + params.warmup_weeks * span
    starts = [evaluation + index * span for index in range(params.eval_weeks)]
    return (stitched_return(series, universe, starts, span),
            rebalanced_return(series, universe, starts, span))


def _overlaps(start: int, length: int, taken: list[Selection]) -> bool:
    return any(start < chosen.end and chosen.start < start + length for chosen in taken)


def choose(series: Series, candidates: list[str], params: Params, rng: random.Random,
           universe_size: int = 20, tries: int = 2000, floor: float = PRICE_FLOOR) -> list[Selection]:
    """One window per kind. Windows never overlap; each kind draws its own universe from what trades there."""
    span = params.span
    length = (params.warmup_weeks + params.eval_weeks) * span
    index = build_index(series, candidates, floor)
    starts = window_starts(series, candidates, params, universe_size, floor, index)
    if not starts:
        raise GenerationError(f"no window holds {universe_size} tradable assets")

    chosen: list[Selection] = []
    for kind in KINDS:
        picked = None
        for _ in range(tries):
            start = rng.choice(starts)
            if _overlaps(start, length, chosen):
                continue
            universe = sorted(rng.sample(alive_in(index, candidates, start, start + length), universe_size))
            held, rebalanced = evaluation_returns(series, universe, start, params)
            if kind in params.targets and not in_target(kind, (held, rebalanced), params):
                continue
            picked = Selection(kind, start, start + length, universe, held, rebalanced)
            break
        if picked is None:
            raise GenerationError(f"{kind}: no window in target after {tries} tries")
        chosen.append(picked)
    return chosen


def reachable(series: Series, candidates: list[str], params: Params, rng: random.Random,
              universe_size: int = 20, samples: int = 500, floor: float = PRICE_FLOOR) -> dict:
    """Stage 1: how many windows exist and how often a random draw lands in each target."""
    length = (params.warmup_weeks + params.eval_weeks) * params.span
    index = build_index(series, candidates, floor)
    starts = window_starts(series, candidates, params, universe_size, floor, index)
    hits = {kind: 0 for kind in params.targets}
    returns = []
    for _ in range(samples):
        if not starts:
            break
        start = rng.choice(starts)
        universe = sorted(rng.sample(alive_in(index, candidates, start, start + length), universe_size))
        held, rebalanced = evaluation_returns(series, universe, start, params)
        returns.append(held)
        for kind in hits:
            if in_target(kind, (held, rebalanced), params):
                hits[kind] += 1
    returns.sort()
    return {
        "windows": len(starts),
        "samples": len(returns),
        "hit_rates": {kind: count / len(returns) if returns else 0.0 for kind, count in hits.items()},
        "held_return": {
            "p10": returns[len(returns) // 10] if returns else None,
            "median": returns[len(returns) // 2] if returns else None,
            "p90": returns[len(returns) * 9 // 10] if returns else None,
        },
    }


def lift(series: Series, universe: list[str], selection: Selection, virtual_start: int) -> list[Bar]:
    """Copy the window's real candles onto virtual dates. Prices, volumes and the hour of the week are untouched."""
    offset = virtual_start - selection.start
    bars = []
    for asset in universe:
        for candle in series[asset].between(selection.start, selection.end):
            bars.append(Bar(asset, 60, candle.t + offset,
                            to_decimal(candle.o), to_decimal(candle.h), to_decimal(candle.l), to_decimal(candle.c),
                            to_decimal(candle.v).quantize(VOLUME_PLACES, rounding=ROUND_HALF_UP, context=WIDE),
                            to_decimal(candle.a).quantize(AMOUNT_PLACES, rounding=ROUND_HALF_UP, context=WIDE)))
    return bars


def integrity(series: Series, selection: Selection, hourly: list[Bar], virtual_start: int) -> dict:
    """Stage 2: moving the window to virtual dates must not change a single value (ADR-0018)."""
    offset = virtual_start - selection.start
    problems: list[str] = []
    if offset % (7 * DAY):
        problems.append("offset is not a whole number of weeks, so weekdays shift")
    moved: dict[str, list[Bar]] = {}
    for bar in hourly:
        moved.setdefault(bar.asset, []).append(bar)
    compared = 0
    for asset in selection.universe:
        source = series[asset].between(selection.start, selection.end)
        bars = sorted(moved.get(asset, []), key=lambda item: item.t)
        if len(bars) != len(source):
            problems.append(f"{asset}: {len(bars)} bars for {len(source)} source candles")
            continue
        for bar, candle in zip(bars, source):
            compared += 1
            if bar.t - offset != candle.t:
                problems.append(f"{asset} t={bar.t}: moved by {bar.t - candle.t} instead of {offset}")
                break
            if (float(bar.o), float(bar.h), float(bar.l), float(bar.c)) != (candle.o, candle.h, candle.l, candle.c):
                problems.append(f"{asset} t={bar.t}: price changed")
                break
    return {"compared": compared, "problems": problems, "passed": not problems}


def identifiability(series: Series, candidates: list[str], params: Params, universe: list[str], kind: str,
                    floor: float = PRICE_FLOOR) -> dict:
    """Stage 3: how far the public facts narrow the window down, with the universe hidden and disclosed."""
    index = build_index(series, candidates, floor)
    length = (params.warmup_weeks + params.eval_weeks) * params.span
    starts = window_starts(series, candidates, params, len(universe), floor, index)
    disclosed = 0
    combinations = 0.0
    for start in starts:
        alive_here = alive_in(index, candidates, start, start + length)
        combinations += math.comb(len(alive_here), len(universe))
        if not set(universe) <= set(alive_here):
            continue
        if kind not in params.targets or in_target(kind, evaluation_returns(series, universe, start, params), params):
            disclosed += 1
    return {
        "windows": len(starts),
        "disclosed_windows": disclosed,
        "hidden_candidates_log10": math.log10(combinations) if combinations else 0.0,
    }


KRW_TICKS = (
    (1_000_000, "1000"), (500_000, "500"), (100_000, "100"), (50_000, "50"), (10_000, "10"),
    (5_000, "5"), (1_000, "1"), (100, "1"), (10, "0.1"), (1, "0.01"), (0.1, "0.001"), (0.01, "0.0001"),
)


def krw_tick(price: float) -> Decimal:
    """Upbit's KRW tick table, the same one the engine settles on (KrwPriceTickSizeResolver)."""
    for floor, tick in KRW_TICKS:
        if price >= floor:
            return Decimal(tick)
    return Decimal("0.00001")


def check_window_bars(bars: list[Bar]) -> list[str]:
    """Bar invariants for a real window. The price-grid test is off: Upbit's tick table changed over the years,
    so old candles sit on a grid today's rule rejects (BCH at 1,172,500 is a 500 KRW step, not 1,000). What the
    grid test guards against - prices we mangled - `integrity` proves directly against the source."""
    return check_bars(bars, {}, tick_of=krw_tick, grid=False)
