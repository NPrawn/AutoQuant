"""Tiny synthetic hourly markets for contest tests."""

import csv
import gzip
import random

from contest.blocks import DAY, HOUR, Candle, Series, Track, format_time
from contest.sampling import BEAR, BULL, SIDEWAYS

MONDAY = 1514764800  # 2018-01-01T00:00:00Z, a Monday
WEEK = 7 * DAY
# 4 chained weeks of patterned_series(up=1.06, down=0.96) return +7.9%, +1.8% or -2.3%
PATTERN_TARGETS = {BULL: (0.05, 0.10), SIDEWAYS: (0.0, 0.03), BEAR: (-0.03, 0.0)}
SOURCE_COLUMNS = [
    "market_code", "asset_id", "open_time", "open_price", "high_price",
    "low_price", "close_price", "trade_volume", "trade_amount",
]


def weekly_track(market: str, start: int, closes: list[float], volumes: list[float] | None = None) -> Track:
    """Hourly candles; every candle of week i is flat at closes[i] with volume volumes[i]."""
    track = Track(market)
    for week, price in enumerate(closes):
        volume = 10.0 if volumes is None else volumes[week]
        for hour in range(7 * 24):
            t = start + week * WEEK + hour * HOUR
            track.candles.append(Candle(t, price, price, price, price, volume, volume * price))
    track.finish()
    return track


def patterned_series(assets: int = 2, cycles: int = 10, up: float = 1.02, down: float = 0.98) -> Series:
    """Weeks 1..3*cycles repeat UP, FLAT, DOWN. Week 0 and the last week only pad the pool."""
    multipliers = [up, 1.0, down] * cycles
    series: Series = {}
    for index in range(assets):
        price = 100.0 * (index + 1)
        closes = [price]
        for multiplier in multipliers:
            price *= multiplier
            closes.append(round(price, 6))
        closes.append(closes[-1])
        series[f"asset-{index}"] = weekly_track(f"KRW-A{index}", MONDAY, closes)
    return series


def random_walk_series(assets: int = 3, weeks: int = 40, seed: int = 0) -> Series:
    """Hourly random walks on a 0.01 price grid with varying volume."""
    rng = random.Random(seed)
    series: Series = {}
    for index in range(assets):
        track = Track(f"KRW-R{index}")
        price = 1000.0 * (index + 1)
        for hour in range(weeks * 7 * 24):
            open_price = price
            price = round(max(price * (1 + rng.gauss(0, 0.01)), 1.0), 2)
            high = round(max(open_price, price) + 0.01 * rng.randint(0, 5), 2)
            low = round(min(open_price, price) - 0.01 * rng.randint(0, 5), 2)
            volume = round(rng.uniform(1, 100), 4)
            amount = volume * (open_price + price) / 2
            track.candles.append(Candle(MONDAY + hour * HOUR, open_price, high, low, price, volume, amount))
        track.finish()
        series[f"random-{index}"] = track
    return series


def write_source_csv(path: str, series: Series) -> None:
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "wt", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(SOURCE_COLUMNS)
        for asset, track in series.items():
            for c in track.candles:
                writer.writerow([
                    track.market, asset, format_time(c.t),
                    repr(c.o), repr(c.h), repr(c.l), repr(c.c), repr(c.v), repr(c.a),
                ])
