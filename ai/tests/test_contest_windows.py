"""Real source windows: who trades through them, which ones hit a regime target, and that they never overlap."""

import random

import pytest

from contest.blocks import HOUR, Candle
from contest.sampling import BULL, GenerationError, Params
from contest.windows import alive, choose, evaluation_returns, reachable, window_starts
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
