"""Chain sampled weeks into one continuous market, aggregate higher intervals, write load CSVs (ADR-0011 steps 5-7)."""

import csv
import math
import statistics
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Context, Decimal, localcontext

from contest.blocks import HOUR, Series, Track, Week, format_time

PRICE_PLACES = Decimal("1e-12")
VOLUME_PLACES = Decimal("1e-18")
AMOUNT_PLACES = Decimal("1e-8")
WIDE = Context(prec=60)  # volume has up to 18 decimals on top of 12+ integer digits
SEAM_FLOOR, SEAM_CEILING = 0.5, 2.0  # a seam may halve or double the level, never more
SEAM_ALPHA, SEAM_HOURS = 0.5, 24     # chosen from the stage 2 sweep (ADR-0017)
LOAD_COLUMNS = [
    "asset_id", "interval_minutes", "open_time", "open_price", "high_price", "low_price",
    "close_price", "trade_volume", "trade_amount", "last_event_time",
]


@dataclass(frozen=True)
class Bar:
    asset: str
    interval: int     # minutes
    t: int            # open time, epoch seconds UTC
    o: Decimal
    h: Decimal
    l: Decimal
    c: Decimal
    v: Decimal
    a: Decimal


def to_decimal(x: float) -> Decimal:
    return Decimal(repr(x))


def tick_table(series: Series) -> dict[int, Decimal]:
    """Smallest gap between distinct observed prices, per decade of price."""
    prices: dict[int, set[Decimal]] = {}
    for track in series.values():
        for candle in track.candles:
            for price in (candle.o, candle.h, candle.l, candle.c):
                if price > 0:
                    prices.setdefault(math.floor(math.log10(price)), set()).add(to_decimal(price))
    table = {}
    for exponent, values in prices.items():
        ordered = sorted(values)
        gaps = [high - low for low, high in zip(ordered, ordered[1:])]
        if gaps:
            table[exponent] = min(gaps)
    if not table:
        raise ValueError("cannot infer tick sizes: need two distinct prices in one decade")
    return table


def tick_for(price: float, table: dict[int, Decimal]) -> Decimal:
    exponent = math.floor(math.log10(price))
    if exponent in table:
        return table[exponent]
    nearest = min(table, key=lambda known: (abs(known - exponent), known))
    return table[nearest].scaleb(exponent - nearest)


def snap(price: float, table: dict[int, Decimal]) -> Decimal:
    tick = tick_for(price, table)
    steps = (to_decimal(price) / tick).to_integral_value(rounding=ROUND_HALF_UP)
    return (tick * max(steps, Decimal(1))).quantize(PRICE_PLACES, context=WIDE)


def _returns(closes: list[float]) -> list[float]:
    return [later / earlier - 1 for earlier, later in zip(closes, closes[1:]) if earlier > 0]


def _spread_gain(before: list[float], after: list[float], alpha: float) -> float:
    """How much to compress or stretch the appended week's first hours, from the two volatility levels."""
    sd_before = statistics.pstdev(_returns(before)) if len(before) >= 3 else 0.0
    sd_after = statistics.pstdev(_returns(after)) if len(after) >= 3 else 0.0
    if sd_before <= 0 or sd_after <= 0:
        return 1.0
    return _clamp((sd_before / sd_after) ** alpha)


def _level_gain(before: list[float], after: list[float], alpha: float) -> float:
    """Same for volume, from the two median levels."""
    if not before or not after:
        return 1.0
    median_before, median_after = statistics.median(before), statistics.median(after)
    if median_before <= 0 or median_after <= 0:
        return 1.0
    return _clamp((median_before / median_after) ** alpha)


def _clamp(gain: float) -> float:
    return min(max(gain, SEAM_FLOOR), SEAM_CEILING)


def _slice(points: list[tuple[int, float]], start: int, end: int) -> list[float]:
    return [value for t, value in points if start <= t < end]


def _volume_ratio(track: Track, start: int, span: int, ref_weeks: int, generated: list[list[float]]) -> float:
    if not generated:
        return 1.0
    recent = [volume for week in generated[-ref_weeks:] for volume in week]
    source = [candle.v for candle in track.between(start - ref_weeks * span, start)]
    if not recent or not source:
        return 1.0
    source_median = statistics.median(source)
    return statistics.median(recent) / source_median if source_median > 0 else 1.0


def stitch(series: Series, universe: list[str], sequence: list[Week], span: int, volume_ref_weeks: int,
           virtual_start: int, ticks: dict[int, Decimal], seam_alpha: float = SEAM_ALPHA,
           seam_hours: int = SEAM_HOURS) -> list[Bar]:
    """Chain the sampled weeks. With `seam_alpha` above 0 the first `seam_hours` of each appended week are scaled
    toward the volatility and volume level the generated market already had, fading out over the ramp."""
    bars: list[Bar] = []
    ramp = seam_hours * HOUR
    for asset in universe:
        track = series[asset]
        generated_close: float | None = None
        generated_volumes: list[list[float]] = []
        tail_closes: list[tuple[int, float]] = []
        tail_volumes: list[tuple[int, float]] = []
        for index, week in enumerate(sequence):
            base = track.last_close_before(week.start)
            k = 1.0 if generated_close is None else generated_close / base
            ratio = _volume_ratio(track, week.start, span, volume_ref_weeks, generated_volumes)
            offset = virtual_start + index * span - week.start
            candles = track.between(week.start, week.start + span)
            head = [candle for candle in candles if candle.t < week.start + ramp]
            spread, level = 1.0, 1.0
            if seam_alpha > 0 and generated_close is not None and ramp > 0:
                # the hours just before the seam. Matching the same hours one week back keeps the weekday rhythm
                # but leaves the incoming week's own rhythm on top of it, and measured worse (ADR-0017)
                seam = virtual_start + index * span
                spread = _spread_gain(_slice(tail_closes, seam - ramp, seam),
                                      [candle.c for candle in head], seam_alpha)
                level = _level_gain(_slice(tail_volumes, seam - ramp, seam),
                                    [candle.v * ratio for candle in head], seam_alpha)
            matched = spread != 1.0 or level != 1.0
            volumes = []
            current = None if base is None else base * k
            source_previous = base
            for candle in candles:
                weight = max(0.0, 1.0 - (candle.t - week.start) / ramp) if matched else 0.0
                shape = 1.0 + weight * (spread - 1.0)
                if matched:
                    close = current * (candle.c / source_previous) ** shape
                    scale = close / candle.c
                else:
                    scale, close = k, candle.c * k
                prices = [snap(close * (price / candle.c) ** shape, ticks)
                          for price in (candle.o, candle.h, candle.l, candle.c)]
                volume = candle.v * ratio * (1.0 + weight * (level - 1.0))
                volumes.append(volume)
                bars.append(Bar(
                    asset, 60, candle.t + offset,
                    prices[0], max(prices), min(prices), prices[3],
                    to_decimal(volume).quantize(VOLUME_PLACES, rounding=ROUND_HALF_UP, context=WIDE),
                    to_decimal(candle.a * scale * ratio * (1.0 + weight * (level - 1.0)))
                    .quantize(AMOUNT_PLACES, rounding=ROUND_HALF_UP, context=WIDE),
                ))
                tail_closes.append((candle.t + offset, close))
                tail_volumes.append((candle.t + offset, volume))
                current, source_previous = close, candle.c
            generated_volumes.append(volumes)
            generated_close = current
            edge = virtual_start + index * span - ramp
            tail_closes = [point for point in tail_closes if point[0] >= edge]
            tail_volumes = [point for point in tail_volumes if point[0] >= edge]
    return bars


def fill_gaps(hourly: list[Bar]) -> tuple[list[Bar], int]:
    """Repeat the previous close with zero volume for hours without trades, between each asset's first and last bar.

    ponytail: local-contest workaround. ADR-0005 keeps no-trade hours empty, but the backtest engine cannot value
    a held coin in an hour without a candle. Drop this once the engine carries the last close forward."""
    by_asset: dict[str, list[Bar]] = {}
    for bar in sorted(hourly, key=lambda item: (item.asset, item.t)):
        by_asset.setdefault(bar.asset, []).append(bar)
    filled: list[Bar] = []
    for bars in by_asset.values():
        for previous, following in zip(bars, bars[1:]):
            filled.append(previous)
            filled += [Bar(previous.asset, 60, t, previous.c, previous.c, previous.c, previous.c, Decimal(0), Decimal(0))
                       for t in range(previous.t + HOUR, following.t, HOUR)]
        filled.append(bars[-1])
    return filled, len(filled) - len(hourly)


def aggregate(bars: list[Bar], interval: int) -> list[Bar]:
    bucket = interval * 60
    groups: dict[tuple[str, int], list[Bar]] = {}
    for bar in sorted((bar for bar in bars if bar.interval == 60), key=lambda item: (item.asset, item.t)):
        groups.setdefault((bar.asset, bar.t - bar.t % bucket), []).append(bar)
    with localcontext(WIDE):
        return [
            Bar(asset, interval, start, group[0].o, max(bar.h for bar in group), min(bar.l for bar in group),
                group[-1].c, sum((bar.v for bar in group), Decimal(0)), sum((bar.a for bar in group), Decimal(0)))
            for (asset, start), group in groups.items()
        ]


def write_load_csv(path: str, bars: list[Bar]) -> None:
    """CSV for `\\copy market_data.minute_candle (LOAD_COLUMNS) FROM STDIN WITH (FORMAT csv, HEADER true)`."""
    with open(path, "w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file, lineterminator="\n")
        writer.writerow(LOAD_COLUMNS)
        for bar in sorted(bars, key=lambda item: (item.interval, item.asset, item.t)):
            writer.writerow([
                bar.asset, bar.interval, format_time(bar.t),
                f"{bar.o:f}", f"{bar.h:f}", f"{bar.l:f}", f"{bar.c:f}", f"{bar.v:f}", f"{bar.a:f}",
                format_time(bar.t + bar.interval * 60 - 1),
            ])
