"""Seam scale matching: the first hours of an appended week take the previous week's volatility and volume level."""

import statistics
from decimal import Decimal

from contest.blocks import HOUR, UP, Week, parse_time
from contest.checks import check_bars
from contest.stitch import aggregate, stitch, tick_table
from tests.contest_fixtures import MONDAY, WEEK, random_walk_series, two_speed_track

VIRTUAL = parse_time("2030-01-07T00:00:00Z")
TICKS = {3: Decimal("0.01")}
CALM, WILD = 0.006, 0.012


def _sequence(*week_indexes: int) -> list[Week]:
    return [Week(MONDAY + index * WEEK, UP, 0.0) for index in week_indexes]


def _hourly_sd(bars, start: int, hours: int) -> float:
    closes = [float(bar.c) for bar in bars if start <= bar.t < start + hours * HOUR]
    return statistics.pstdev([later / earlier - 1 for earlier, later in zip(closes, closes[1:])])


def _median_volume(bars, start: int, hours: int) -> float:
    return statistics.median([float(bar.v) for bar in bars if start <= bar.t < start + hours * HOUR])


def test_seam_matching_is_on_by_default():
    series = {"a": two_speed_track("KRW-A", MONDAY, [CALM, WILD, CALM])}
    sequence = _sequence(0, 1)

    assert (stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS)
            == stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS, seam_alpha=0.5, seam_hours=24))


def test_seam_matching_switched_off_scales_each_week_rigidly():
    series = {"a": two_speed_track("KRW-A", MONDAY, [CALM, WILD, CALM])}
    sequence = _sequence(0, 1)

    bars = stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS, seam_alpha=0.0)

    source = [candle.c for candle in series["a"].between(MONDAY + WEEK, MONDAY + 2 * WEEK)]
    scales = [float(bar.c) / close for bar, close in zip(bars[168:], source)]
    assert max(scales) - min(scales) < 1e-4


def test_seam_matching_pulls_the_first_day_volatility_toward_the_previous_week():
    series = {"a": two_speed_track("KRW-A", MONDAY, [CALM, WILD, CALM])}
    sequence = _sequence(0, 1)

    plain = stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS, seam_alpha=0.0)
    matched = stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS, seam_alpha=1.0, seam_hours=24)

    before = _hourly_sd(plain, VIRTUAL + WEEK - 24 * HOUR, 24)
    jump = _hourly_sd(plain, VIRTUAL + WEEK, 24) / before
    closed = _hourly_sd(matched, VIRTUAL + WEEK, 24) / before
    assert jump > 1.8
    assert 1.0 < closed < 1 + (jump - 1) * 0.8


def test_seam_matching_fades_out_after_the_ramp():
    series = {"a": two_speed_track("KRW-A", MONDAY, [CALM, WILD, CALM])}
    sequence = _sequence(0, 1)

    plain = stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS, seam_alpha=0.0)
    matched = stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS, seam_alpha=1.0, seam_hours=24)

    tail = VIRTUAL + WEEK + 48 * HOUR
    assert abs(_hourly_sd(matched, tail, 48) / _hourly_sd(plain, tail, 48) - 1) < 0.02


def test_seam_matching_pulls_the_first_day_volume_toward_the_previous_week():
    series = {"a": two_speed_track("KRW-A", MONDAY, [CALM, CALM, CALM], volumes=[10.0, 25.0, 10.0])}
    sequence = _sequence(0, 1)

    plain = stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS, seam_alpha=0.0)
    matched = stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS, seam_alpha=1.0, seam_hours=24)

    before = _median_volume(plain, VIRTUAL + WEEK - 24 * HOUR, 24)
    jump = _median_volume(plain, VIRTUAL + WEEK, 24) / before
    closed = _median_volume(matched, VIRTUAL + WEEK, 24) / before
    assert jump > 2
    assert 1.0 < closed < 1 + (jump - 1) * 0.8


def test_seam_matching_keeps_bar_invariants():
    series = random_walk_series(assets=2, weeks=8)
    ticks = tick_table(series)
    sequence = _sequence(1, 5, 2, 6)

    hourly = stitch(series, sorted(series), sequence, WEEK, 4, VIRTUAL, ticks, seam_alpha=1.0, seam_hours=24)

    assert check_bars(hourly + aggregate(hourly, 240) + aggregate(hourly, 1440), ticks) == []
