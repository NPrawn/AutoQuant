"""Load hourly candles and cut them into labeled week blocks (ADR-0011)."""

import bisect
import csv
import gzip
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone

HOUR = 3600
DAY = 24 * HOUR
UP, FLAT, DOWN = "UP", "FLAT", "DOWN"
LABELS = (UP, FLAT, DOWN)

_SHORT_OFFSET = re.compile(r"([+-]\d\d)$")


@dataclass(frozen=True)
class Candle:
    t: int      # open time, epoch seconds UTC
    o: float
    h: float
    l: float
    c: float
    v: float    # trade_volume
    a: float    # trade_amount


@dataclass
class Track:
    market: str
    candles: list[Candle] = field(default_factory=list)
    times: list[int] = field(default_factory=list)

    def finish(self) -> None:
        self.candles.sort(key=lambda candle: candle.t)
        self.times = [candle.t for candle in self.candles]

    def last_close_before(self, t: int) -> float | None:
        index = bisect.bisect_left(self.times, t)
        return self.candles[index - 1].c if index else None

    def between(self, start: int, end: int) -> list[Candle]:
        return self.candles[bisect.bisect_left(self.times, start):bisect.bisect_left(self.times, end)]


Series = dict[str, Track]


@dataclass(frozen=True)
class Week:
    start: int
    label: str
    ret: float


def parse_time(text: str) -> int:
    normalized = _SHORT_OFFSET.sub(r"\1:00", text.strip())
    moment = datetime.fromisoformat(normalized)
    if moment.tzinfo is None:
        raise ValueError(f"open_time needs a UTC offset: {text!r}")
    return int(moment.timestamp())


def format_time(t: int) -> str:
    return datetime.fromtimestamp(t, timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def load_source(path: str, assets: set[str] | None = None) -> Series:
    opener = gzip.open if path.endswith(".gz") else open
    series: Series = {}
    with opener(path, "rt", encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            asset = row["asset_id"]
            if assets is not None and asset not in assets:
                continue
            track = series.setdefault(asset, Track(row["market_code"]))
            track.candles.append(Candle(
                parse_time(row["open_time"]),
                float(row["open_price"]), float(row["high_price"]),
                float(row["low_price"]), float(row["close_price"]),
                float(row["trade_volume"]), float(row["trade_amount"]),
            ))
    for track in series.values():
        track.finish()
    return series


def first_week_start(t: int, weekday: int) -> int:
    """Earliest 00:00 UTC on `weekday` (Monday=0) at or after t."""
    day = -(-t // DAY)
    return (day + (weekday - (day + 3)) % 7) * DAY  # 1970-01-01 was a Thursday


def build_pool(series: Series, universe: list[str], weekday: int, block_days: int = 7) -> list[int]:
    """Week starts where every universe asset traded before the week and after it ends."""
    span = block_days * DAY
    tracks = [series[asset] for asset in universe]
    start = first_week_start(min(track.times[0] for track in tracks), weekday)
    last = max(track.times[-1] for track in tracks)
    pool = []
    while start + span <= last:
        if all(track.times[0] < start and track.times[-1] >= start + span for track in tracks):
            pool.append(start)
        start += span
    return pool


def period_return(track: Track, start: int, length: int) -> float:
    return track.last_close_before(start + length) / track.last_close_before(start) - 1


def week_return(series: Series, universe: list[str], start: int, span: int) -> float:
    return sum(period_return(series[asset], start, span) for asset in universe) / len(universe)


def label_weeks(series: Series, universe: list[str], pool: list[int], span: int) -> list[Week]:
    scored = sorted((week_return(series, universe, start, span), start) for start in pool)
    third = len(scored) // 3
    labels = {}
    for rank, (_, start) in enumerate(scored):
        labels[start] = DOWN if rank < third else UP if rank >= len(scored) - third else FLAT
    returns = {start: ret for ret, start in scored}
    return [Week(start, labels[start], returns[start]) for start in pool]


def transition_matrix(weeks: list[Week], span: int) -> dict[str, dict[str, float]]:
    by_start = {week.start: week for week in weeks}
    counts = {src: {dst: 0 for dst in LABELS} for src in LABELS}
    for week in weeks:
        following = by_start.get(week.start + span)
        if following is not None:
            counts[week.label][following.label] += 1
    matrix = {}
    for src, row in counts.items():
        total = sum(row.values())
        matrix[src] = {dst: (count / total if total else 1 / len(LABELS)) for dst, count in row.items()}
    return matrix


def real_windows(weeks: list[Week], n_weeks: int, span: int) -> list[list[Week]]:
    by_start = {week.start: week for week in weeks}
    windows = []
    for week in sorted(weeks, key=lambda item: item.start):
        chain = [by_start.get(week.start + i * span) for i in range(n_weeks)]
        if all(chain):
            windows.append(chain)
    return windows
