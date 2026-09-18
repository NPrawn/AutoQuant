"""Bar invariants and ADR-0012 stage 1 (feasibility) / stage 2 (realism, ADR-0015 tolerance) checks."""

import bisect
import math
import random
import statistics
from decimal import Decimal

from contest.blocks import (
    DAY, HOUR, LABELS, Series, build_pool, label_weeks, period_return, real_windows, transition_matrix,
)
from contest.sampling import BEAR, BULL, SIDEWAYS, GenerationError, Params, regime_hit_rate, sample_general
from contest.stitch import Bar, aggregate, stitch, tick_for, tick_table

MIN_WEEKS_PER_LABEL = 30
Point = tuple[int, float, float]   # (open time, close, volume)


# --- bar invariants ---------------------------------------------------------------------------

def _on_grid(price: Decimal, ticks: dict[int, Decimal]) -> bool:
    exponent = math.floor(math.log10(float(price)))
    # snapping just below a decade edge (9.996 -> 10.00) lands exactly on a power of ten
    return price % tick_for(float(price), ticks) == 0 or price == Decimal(10) ** exponent


def _key(bars: list[Bar]) -> list[tuple]:
    return sorted((bar.asset, bar.t, bar.o, bar.h, bar.l, bar.c, bar.v, bar.a) for bar in bars)


def check_bars(bars: list[Bar], ticks: dict[int, Decimal]) -> list[str]:
    problems: list[str] = []
    for bar in bars:
        where = f"{bar.asset} {bar.interval}m t={bar.t}"
        prices = (bar.o, bar.h, bar.l, bar.c)
        if min(prices) <= 0:
            problems.append(f"{where}: non-positive price")
        if not (bar.l <= min(bar.o, bar.c) and bar.h >= max(bar.o, bar.c)):
            problems.append(f"{where}: OHLC order")
        if bar.v < 0 or bar.a < 0:
            problems.append(f"{where}: negative volume or amount")
        if bar.t % (bar.interval * 60):
            problems.append(f"{where}: open time not on interval boundary")
        if bar.interval != 60:
            continue
        off_grid = [price for price in prices if not _on_grid(price, ticks)]
        if off_grid:
            problems.append(f"{where}: price {off_grid[0]} off tick grid")
        if bar.v > 0:
            tick = tick_for(float(bar.h), ticks)
            average = bar.a / bar.v
            if not bar.l - tick <= average <= bar.h + tick:
                problems.append(f"{where}: amount/volume {average} outside low..high")
    hourly = [bar for bar in bars if bar.interval == 60]
    for interval in (240, 1440):
        if _key([bar for bar in bars if bar.interval == interval]) != _key(aggregate(hourly, interval)):
            problems.append(f"{interval}m bars differ from re-aggregated 60m bars")
    return problems


# --- stage 1: feasibility ---------------------------------------------------------------------

def feasibility(series: Series, universe: list[str], params: Params, seed: int,
                min_weeks: int = MIN_WEEKS_PER_LABEL) -> dict:
    result = {"assets": len(universe), "pool_weeks": 0, "label_weeks": {label: 0 for label in LABELS},
              "matrix": {}, "hit_rates": {}, "passed": False, "reason": ""}
    if len(universe) < 2:
        result["reason"] = "종목이 2개 미만"
        return result
    pool = build_pool(series, universe, params.block_start, params.block_days, params.window)
    weeks = label_weeks(series, universe, pool, params.span)
    result["pool_weeks"] = len(pool)
    result["label_weeks"] = {label: sum(week.label == label for week in weeks) for label in LABELS}
    if min(result["label_weeks"].values()) < min_weeks:
        result["reason"] = f"라벨별 주 수가 {min_weeks}주 미만"
        return result
    matrix = transition_matrix(weeks, params.span)
    rng = random.Random(seed)
    result["matrix"] = matrix
    try:
        result["hit_rates"] = {
            kind: regime_hit_rate(kind, weeks, matrix, series, universe, params, rng) for kind in (BULL, BEAR, SIDEWAYS)
        }
    except GenerationError as error:
        result["reason"] = str(error)
        return result
    unreachable = [kind for kind, rate in result["hit_rates"].items() if rate == 0]
    result["passed"] = not unreachable
    result["reason"] = f"목표 범위에 닿지 못한 장세: {', '.join(unreachable)}" if unreachable else "통과"
    return result


# --- stage 2: realism -------------------------------------------------------------------------

def ks_statistic(a: list[float], b: list[float]) -> float:
    a, b = sorted(a), sorted(b)
    i = j = 0
    distance = 0.0
    while i < len(a) and j < len(b):
        x = min(a[i], b[j])
        while i < len(a) and a[i] <= x:
            i += 1
        while j < len(b) and b[j] <= x:
            j += 1
        distance = max(distance, abs(i / len(a) - j / len(b)))
    return distance


def _equal_weight(series: Series, universe: list[str], start: int, length: int) -> float:
    return sum(period_return(series[asset], start, length) for asset in universe) / len(universe)


def _weekly(series: Series, universe: list[str], starts: list[int], span: int) -> list[float]:
    return [_equal_weight(series, universe, start, span) for start in starts]


def weekly_autocorr(series: Series, universe: list[str], starts: list[int], span: int) -> float:
    returns = _weekly(series, universe, starts, span)
    try:
        return statistics.correlation(returns[:-1], returns[1:])
    except statistics.StatisticsError:
        return 0.0


def monthly_vol(series: Series, universe: list[str], starts: list[int], span: int) -> float:
    returns = _weekly(series, universe, starts, span)
    rolling = [math.prod(1 + r for r in returns[i:i + 4]) - 1 for i in range(len(returns) - 3)]
    return statistics.pstdev(rolling)


def max_drawdown(series: Series, universe: list[str], starts: list[int], span: int) -> float:
    level = peak = 1.0
    worst = 0.0
    for start in starts:
        for day in range(span // DAY):
            level *= 1 + _equal_weight(series, universe, start + day * DAY, DAY)
            peak = max(peak, level)
            worst = max(worst, 1 - level / peak)
    return worst


WINDOW_METRICS = {"weekly_autocorr": weekly_autocorr, "monthly_vol": monthly_vol, "max_drawdown": max_drawdown}


def points_from_series(series: Series, universe: list[str]) -> dict[str, list[Point]]:
    return {asset: [(c.t, c.c, c.v) for c in series[asset].candles] for asset in universe}


def points_from_bars(bars: list[Bar]) -> dict[str, list[Point]]:
    points: dict[str, list[Point]] = {}
    for bar in sorted((bar for bar in bars if bar.interval == 60), key=lambda item: (item.asset, item.t)):
        points.setdefault(bar.asset, []).append((bar.t, float(bar.c), float(bar.v)))
    return points


def _hourly_sd(rows: list[Point]) -> float:
    closes = [row[1] for row in rows]
    returns = [later / earlier - 1 for earlier, later in zip(closes, closes[1:]) if earlier > 0]
    return statistics.pstdev(returns) if len(returns) >= 2 else 0.0


def seam_values(points: dict[str, list[Point]], boundaries: list[int], hours: int = 24) -> dict[str, list[float]]:
    """Log change of hourly volatility and of median volume across each boundary, in boundary order."""
    values: dict[str, list[float]] = {"vol_jump": [], "volume_jump": []}
    indexed = [(rows, [row[0] for row in rows]) for rows in points.values()]
    for boundary in boundaries:
        for rows, times in indexed:
            before = rows[bisect.bisect_left(times, boundary - hours * HOUR):bisect.bisect_left(times, boundary)]
            after = rows[bisect.bisect_left(times, boundary):bisect.bisect_left(times, boundary + hours * HOUR)]
            sd_before, sd_after = _hourly_sd(before), _hourly_sd(after)
            if sd_before > 0 and sd_after > 0:
                values["vol_jump"].append(math.log(sd_after / sd_before))
            if before and after:
                volume_before = statistics.median(row[2] for row in before)
                volume_after = statistics.median(row[2] for row in after)
                if volume_before > 0 and volume_after > 0:
                    values["volume_jump"].append(math.log(volume_after / volume_before))
    return values


def halves_distance(values: list[float]) -> float:
    """KS distance between the first and second half of time-ordered real values: how much real data differs
    from itself (ADR-0015)."""
    half = len(values) // 2
    return ks_statistic(values[:half], values[half:])


def compare(real: list[float], generated: list[float]) -> dict:
    """`real` must be in time order. Passes when generated data is no farther from real than real is from itself."""
    summary = {"real_n": len(real), "generated_n": len(generated)}
    if len(real) < 4 or len(generated) < 2:
        return {**summary, "passed": False, "reason": "표본 부족"}
    distance = ks_statistic(real, generated)
    tolerance = halves_distance(real)
    return {**summary, "real_median": statistics.median(real), "generated_median": statistics.median(generated),
            "ks": distance, "tolerance": tolerance, "passed": distance <= tolerance, "reason": ""}


def stage2(series: Series, universe: list[str], params: Params, seed: int,
           generated: int = 200, seam_datasets: int = 50) -> dict:
    span = params.span
    length = params.warmup_weeks + params.eval_weeks
    pool = build_pool(series, universe, params.block_start, params.block_days, params.window)
    weeks = label_weeks(series, universe, pool, span)
    matrix = transition_matrix(weeks, span)
    real = [[week.start for week in window] for window in real_windows(weeks, length, span)]
    rng = random.Random(seed)
    sampled = [sample_general(weeks, matrix, length, rng) for _ in range(generated)]

    metrics = {}
    for name, metric in WINDOW_METRICS.items():
        metrics[name] = compare(
            [metric(series, universe, starts, span) for starts in real],
            [metric(series, universe, [week.start for week in sequence], span) for sequence in sampled],
        )

    pool_set = set(pool)
    real_seams = seam_values(points_from_series(series, universe), [s for s in pool if s - span in pool_set])
    ticks = tick_table(series)
    generated_seams: dict[str, list[float]] = {"vol_jump": [], "volume_jump": []}
    violations = 0
    for sequence in sampled[:seam_datasets]:
        hourly = stitch(series, universe, sequence, span, params.volume_ref_weeks, params.virtual_start, ticks)
        violations += len(check_bars(hourly + aggregate(hourly, 240) + aggregate(hourly, 1440), ticks))
        boundaries = [params.virtual_start + i * span for i in range(1, length)]
        for name, values in seam_values(points_from_bars(hourly), boundaries).items():
            generated_seams[name].extend(values)
    for name in ("vol_jump", "volume_jump"):
        metrics[name] = compare(real_seams[name], generated_seams[name])

    return {
        "real_windows": len(real),
        "generated": generated,
        "seam_datasets": min(seam_datasets, generated),
        "violations": violations,
        "metrics": metrics,
        "passed": violations == 0 and all(metric["passed"] for metric in metrics.values()),
    }
