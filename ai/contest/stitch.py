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


def _decimal(x: float) -> Decimal:
    return Decimal(repr(x))


def tick_table(series: Series) -> dict[int, Decimal]:
    """Smallest gap between distinct observed prices, per decade of price."""
    prices: dict[int, set[Decimal]] = {}
    for track in series.values():
        for candle in track.candles:
            for price in (candle.o, candle.h, candle.l, candle.c):
                if price > 0:
                    prices.setdefault(math.floor(math.log10(price)), set()).add(_decimal(price))
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
    steps = (_decimal(price) / tick).to_integral_value(rounding=ROUND_HALF_UP)
    return (tick * max(steps, Decimal(1))).quantize(PRICE_PLACES, context=WIDE)


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
           virtual_start: int, ticks: dict[int, Decimal]) -> list[Bar]:
    bars: list[Bar] = []
    for asset in universe:
        track = series[asset]
        generated_close: float | None = None
        generated_volumes: list[list[float]] = []
        for index, week in enumerate(sequence):
            base = track.last_close_before(week.start)
            k = 1.0 if generated_close is None else generated_close / base
            ratio = _volume_ratio(track, week.start, span, volume_ref_weeks, generated_volumes)
            offset = virtual_start + index * span - week.start
            volumes = []
            for candle in track.between(week.start, week.start + span):
                prices = [snap(price * k, ticks) for price in (candle.o, candle.h, candle.l, candle.c)]
                volume = candle.v * ratio
                volumes.append(volume)
                bars.append(Bar(
                    asset, 60, candle.t + offset,
                    prices[0], max(prices), min(prices), prices[3],
                    _decimal(volume).quantize(VOLUME_PLACES, rounding=ROUND_HALF_UP, context=WIDE),
                    _decimal(candle.a * k * ratio).quantize(AMOUNT_PLACES, rounding=ROUND_HALF_UP, context=WIDE),
                ))
            generated_volumes.append(volumes)
            generated_close = k * track.last_close_before(week.start + span)
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
