"""Real source windows: who trades through them, which ones hit a regime target, and that they never overlap."""

import math
import random
from decimal import Decimal
from dataclasses import replace

import pytest

from contest.blocks import HOUR
from contest.stitch import aggregate
from contest.sampling import BULL, GenerationError, Params
from contest.windows import (
    Selection, alive, check_window_bars, choose, evaluation_returns, identifiability, integrity, krw_tick,
    lift, reachable, window_starts,
)
from tests.contest_fixtures import MONDAY, WEEK, weekly_track

PARAMS = Params(warmup_weeks=1, eval_weeks=2)


def _series(closes: dict[str, list[float]]) -> dict:
    return {asset: weekly_track(f"KRW-{asset.upper()}", MONDAY, values) for asset, values in closes.items()}


def test_alive_drops_assets_that_start_late_or_trade_under_one_won():
    series = _series({"a": [10.0] * 6, "late": [10.0] * 6, "penny": [10.0, 10.0, 0.5, 10.0, 10.0, 10.0]})
    series["late"].candles = series["late"].candles[3 * 7 * 24:]
    series["late"].finish()

    assert alive(series, ["a", "late", "penny"], MONDAY + WEEK, MONDAY + 4 * WEEK) == ["a"]


def test_window_starts_lists_every_aligned_start_with_enough_assets():
    series = _series({"a": [10.0] * 8, "b": [10.0] * 8})

    starts = window_starts(series, ["a", "b"], PARAMS, minimum=2)

    assert starts == [MONDAY + index * WEEK for index in range(1, 5)]
    assert window_starts(series, ["a", "b"], PARAMS, minimum=3) == []


def test_evaluation_returns_skip_the_warmup_weeks():
    # week 0 is warmup; the two evaluation weeks move 10 -> 10 -> 20, so both measures double
    series = _series({"a": [10.0, 10.0, 20.0, 40.0, 40.0]})

    held, rebalanced = evaluation_returns(series, ["a"], MONDAY, PARAMS)

    assert held == pytest.approx(1.0)
    assert rebalanced == pytest.approx(1.0)


def test_choose_picks_one_window_per_kind_without_overlap():
    rising = [10.0 * 1.1 ** step for step in range(30)]
    series = _series({"a": rising, "b": rising})
    params = Params(warmup_weeks=1, eval_weeks=2, targets={BULL: (0.15, 0.25)})

    chosen = choose(series, ["a", "b"], params, random.Random(0), universe_size=2, tries=200)

    assert [selection.kind for selection in chosen] == ["GENERAL", "BULL", "BEAR", "SIDEWAYS"]
    spans = sorted((selection.start, selection.end) for selection in chosen)
    assert all(earlier[1] <= later[0] for earlier, later in zip(spans, spans[1:]))
    bull = next(selection for selection in chosen if selection.kind == BULL)
    assert 0.15 <= bull.held <= 0.25 and bull.universe == ["a", "b"]


def test_choose_reports_when_no_window_reaches_a_target():
    series = _series({"a": [10.0] * 12, "b": [10.0] * 12})
    params = Params(warmup_weeks=1, eval_weeks=2, targets={BULL: (0.15, 0.25)})

    with pytest.raises(GenerationError, match="BULL"):
        choose(series, ["a", "b"], params, random.Random(0), universe_size=2, tries=50)


def test_reachable_counts_windows_and_hit_rates():
    rising = [10.0 * 1.1 ** step for step in range(30)]
    series = _series({"a": rising, "b": rising})
    params = Params(warmup_weeks=1, eval_weeks=2, targets={BULL: (0.15, 0.25)})

    report = reachable(series, ["a", "b"], params, random.Random(0), universe_size=2, samples=20)

    assert report["windows"] > 0 and report["samples"] == 20
    assert report["hit_rates"][BULL] == 1.0
    assert report["held_return"]["median"] == pytest.approx(0.21)


def test_integrity_passes_when_the_window_only_moved():
    series = _series({"a": [10.0, 12.0, 15.0, 15.0]})
    selection = Selection("GENERAL", MONDAY, MONDAY + 3 * WEEK, ["a"], 0.0, 0.0)
    virtual = MONDAY + 100 * WEEK

    report = integrity(series, selection, lift(series, ["a"], selection, virtual), virtual)

    assert report["passed"] and report["compared"] == 3 * 7 * 24


def test_integrity_catches_a_changed_price():
    series = _series({"a": [10.0, 12.0, 15.0, 15.0]})
    selection = Selection("GENERAL", MONDAY, MONDAY + 3 * WEEK, ["a"], 0.0, 0.0)
    virtual = MONDAY + 100 * WEEK
    bars = lift(series, ["a"], selection, virtual)
    bars[5] = replace(bars[5], c=bars[5].c * 2)

    assert integrity(series, selection, bars, virtual)["problems"] == ["a t=%d: price changed" % bars[5].t]


def test_integrity_catches_a_shift_that_moves_the_weekday():
    series = _series({"a": [10.0, 12.0, 15.0, 15.0]})
    selection = Selection("GENERAL", MONDAY, MONDAY + 3 * WEEK, ["a"], 0.0, 0.0)
    virtual = MONDAY + 100 * WEEK + 3 * 24 * HOUR

    report = integrity(series, selection, lift(series, ["a"], selection, virtual), virtual)

    assert report["problems"] == ["offset is not a whole number of weeks, so weekdays shift"]


def test_identifiability_counts_what_a_public_universe_would_give_away():
    rising = [10.0 * 1.1 ** step for step in range(30)]
    series = _series({"a": rising, "b": rising, "c": rising})
    params = Params(warmup_weeks=1, eval_weeks=2, targets={BULL: (0.15, 0.25)})

    report = identifiability(series, ["a", "b", "c"], params, ["a", "b"], BULL)

    # every window rises 21%, so a public universe leaves every window standing
    assert report["disclosed_windows"] == report["windows"] > 0
    # three assets choose two: three universes per window, so the hidden space is larger
    assert report["hidden_candidates_log10"] > math.log10(report["windows"])


def test_check_window_bars_uses_the_exchange_tick_rule():
    # 94.3 sits on the 0.1 tick of the 10~100 band and 1485 on the 1 tick above 1000: both are real Upbit prices
    series = {"a": weekly_track("KRW-A", MONDAY, [94.3, 94.4, 94.5]),
              "b": weekly_track("KRW-B", MONDAY, [1485.0, 1490.0, 1495.0])}
    selection = Selection("GENERAL", MONDAY, MONDAY + 2 * WEEK, ["a", "b"], 0.0, 0.0)
    hourly = lift(series, ["a", "b"], selection, MONDAY + 100 * WEEK)

    assert check_window_bars(hourly + aggregate(hourly, 240) + aggregate(hourly, 1440)) == []


def test_check_window_bars_accepts_an_older_tick_grid():
    # BCH really traded at 1,172,500: a 500 KRW step, which today's 1,000 KRW rule would reject
    series = {"a": weekly_track("KRW-A", MONDAY, [1_172_500.0, 1_212_500.0, 1_205_500.0])}
    selection = Selection("GENERAL", MONDAY, MONDAY + 2 * WEEK, ["a"], 0.0, 0.0)
    hourly = lift(series, ["a"], selection, MONDAY + 100 * WEEK)

    assert check_window_bars(hourly + aggregate(hourly, 240) + aggregate(hourly, 1440)) == []


def test_krw_tick_follows_the_engine_table():
    assert (krw_tick(94.3), krw_tick(1485.0), krw_tick(12_000.0), krw_tick(0.5)) == (
        Decimal("0.1"), Decimal("1"), Decimal("10"), Decimal("0.001"))
