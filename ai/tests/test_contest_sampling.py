import random

import pytest

from contest.blocks import DOWN, FLAT, UP, Week, build_pool, label_weeks, parse_time, transition_matrix
from contest.sampling import (
    BEAR, BULL, GENERAL, SIDEWAYS, GenerationError, Params, in_target, max_factor_period, rebalanced_return,
    regime_hit_rate, sample_general, sample_regime, sample_sequence, stitched_return,
)
from tests.contest_fixtures import MONDAY, PATTERN_TARGETS, WEEK, patterned_series, weekly_track

CYCLE = {UP: DOWN, DOWN: FLAT, FLAT: UP}
CYCLE_MATRIX = {
    UP: {UP: 0.0, FLAT: 0.0, DOWN: 1.0},
    DOWN: {UP: 0.0, FLAT: 1.0, DOWN: 0.0},
    FLAT: {UP: 1.0, FLAT: 0.0, DOWN: 0.0},
}
UNREACHABLE = {BULL: (0.5, 0.6), BEAR: (-0.2, -0.1), SIDEWAYS: (-0.03, 0.03)}


def _labeled_weeks() -> list[Week]:
    labels = [UP] * 10 + [FLAT] * 10 + [DOWN] * 10
    return [Week(i * WEEK, label, 0.0) for i, label in enumerate(labels)]


def _patterned():
    series = patterned_series(assets=2, cycles=10, up=1.06, down=0.96)
    universe = list(series)
    weeks = label_weeks(series, universe, build_pool(series, universe, 0), WEEK)
    return series, universe, weeks, transition_matrix(weeks, WEEK)


def test_params_virtual_start_and_factor_period_caps():
    assert Params().virtual_start == parse_time("2030-01-07T00:00:00Z")
    assert Params(block_start=3).virtual_start == parse_time("2030-01-03T00:00:00Z")
    assert max_factor_period(Params()) == {"1H": 839, "4H": 209, "1D": 34}
    assert Params().candidates == 3000


def test_sample_general_follows_transitions_and_never_repeats_a_week():
    sequence = sample_general(_labeled_weeks(), CYCLE_MATRIX, 9, random.Random(1))

    assert len({week.start for week in sequence}) == 9
    for previous, following in zip(sequence, sequence[1:]):
        assert following.label == CYCLE[previous.label]


def test_sample_general_is_deterministic_for_a_seed():
    first = sample_general(_labeled_weeks(), CYCLE_MATRIX, 6, random.Random(7))
    second = sample_general(_labeled_weeks(), CYCLE_MATRIX, 6, random.Random(7))

    assert first == second


def test_sample_general_raises_when_a_label_runs_out():
    weeks = [Week(0, UP, 0.0)]
    matrix = {label: {UP: 1.0, FLAT: 0.0, DOWN: 0.0} for label in (UP, FLAT, DOWN)}

    with pytest.raises(GenerationError):
        sample_general(weeks, matrix, 2, random.Random(1))


def test_stitched_return_is_equal_weight_buy_and_hold():
    series = {
        "a": weekly_track("KRW-A", MONDAY, [100.0, 110.0, 121.0]),
        "b": weekly_track("KRW-B", MONDAY, [100.0, 90.0, 81.0]),
    }

    ret = stitched_return(series, ["a", "b"], [MONDAY + WEEK, MONDAY + 2 * WEEK], WEEK)

    assert ret == pytest.approx(0.01)


def test_rebalanced_return_resets_weights_every_day():
    series = {
        "a": weekly_track("KRW-A", MONDAY, [100.0, 110.0, 121.0]),
        "b": weekly_track("KRW-B", MONDAY, [100.0, 90.0, 81.0]),
    }
    starts = [MONDAY + WEEK, MONDAY + 2 * WEEK]

    assert rebalanced_return(series, ["a", "b"], starts, WEEK) == pytest.approx(0.0)
    assert stitched_return(series, ["a", "b"], starts, WEEK) == pytest.approx(0.01)


def test_in_target_needs_every_return_inside_the_range():
    params = Params(targets={BULL: (0.2, 0.3), BEAR: (-0.2, -0.1), SIDEWAYS: (-0.03, 0.03)})

    assert in_target(BULL, (0.25, 0.22), params)
    assert not in_target(BULL, (0.25, 0.05), params)


def test_sample_regime_mixes_labels_and_keeps_the_evaluation_return_inside_the_target():
    series, universe, weeks, matrix = _patterned()
    params = Params(warmup_weeks=2, eval_weeks=4, candidates=50, targets=PATTERN_TARGETS)

    for kind, (low, high) in PATTERN_TARGETS.items():
        sequence = sample_regime(kind, weeks, matrix, series, universe, params, random.Random(3))

        assert len({week.start for week in sequence}) == 6
        assert len({week.label for week in sequence[2:]}) > 1
        starts = [week.start for week in sequence[2:]]
        assert low <= stitched_return(series, universe, starts, WEEK) <= high
        assert low <= rebalanced_return(series, universe, starts, WEEK) <= high


def test_sample_regime_raises_when_target_is_unreachable():
    series, universe, weeks, matrix = _patterned()
    params = Params(warmup_weeks=2, eval_weeks=4, candidates=20, targets=UNREACHABLE)

    with pytest.raises(GenerationError):
        sample_regime(BULL, weeks, matrix, series, universe, params, random.Random(3))


def test_regime_hit_rate_counts_candidates_inside_target():
    series, universe, weeks, matrix = _patterned()
    reachable = Params(warmup_weeks=2, eval_weeks=4, candidates=30, targets=PATTERN_TARGETS)
    unreachable = Params(warmup_weeks=2, eval_weeks=4, candidates=30, targets=UNREACHABLE)

    assert 0.0 < regime_hit_rate(BULL, weeks, matrix, series, universe, reachable, random.Random(1)) < 1.0
    assert regime_hit_rate(BULL, weeks, matrix, series, universe, unreachable, random.Random(1)) == 0.0


def test_sample_sequence_general_uses_warmup_plus_evaluation_weeks():
    series, universe, weeks, matrix = _patterned()
    params = Params(warmup_weeks=2, eval_weeks=3)

    assert len(sample_sequence(GENERAL, weeks, matrix, series, universe, params, random.Random(1))) == 5
