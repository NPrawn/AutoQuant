import math
from dataclasses import replace
from decimal import Decimal

import pytest

from contest.blocks import DOWN, FLAT, HOUR, UP, parse_time
from contest.checks import (
    check_bars, compare, feasibility, halves_distance, ks_statistic, max_drawdown, monthly_vol, seam_values,
    stage2, weekly_autocorr,
)
from contest.sampling import BEAR, BULL, SIDEWAYS, Params
from contest.stitch import Bar, aggregate
from tests.contest_fixtures import MONDAY, PATTERN_TARGETS, WEEK, patterned_series, random_walk_series, weekly_track

VIRTUAL = parse_time("2030-01-07T00:00:00Z")
TICKS = {2: Decimal("0.01")}
UNREACHABLE = {BULL: (0.5, 0.6), BEAR: (-0.2, -0.1), SIDEWAYS: (-0.03, 0.03)}


def _dataset() -> list[Bar]:
    hourly = []
    for i in range(8):
        close = Decimal("100.00") + Decimal(i) / 100
        hourly.append(Bar("a", 60, VIRTUAL + i * HOUR, close, close + Decimal("0.05"),
                          close - Decimal("0.05"), close, Decimal(2), close * 2))
    return hourly + aggregate(hourly, 240) + aggregate(hourly, 1440)


def test_valid_dataset_has_no_problems():
    assert check_bars(_dataset(), TICKS) == []


def test_detects_ohlc_order_violation():
    bars = _dataset()
    bars[0] = replace(bars[0], h=bars[0].c - Decimal("0.01"))

    assert any("OHLC" in problem for problem in check_bars(bars, TICKS))


def test_detects_price_off_the_tick_grid():
    bars = _dataset()
    bars[1] = replace(bars[1], o=Decimal("100.005"))

    assert any("tick grid" in problem for problem in check_bars(bars, TICKS))


def test_detects_average_price_outside_low_high():
    bars = _dataset()
    bars[2] = replace(bars[2], a=bars[2].v * 200)

    assert any("amount/volume" in problem for problem in check_bars(bars, TICKS))


def test_detects_higher_interval_bars_that_do_not_match_hourly_bars():
    bars = _dataset()
    index = next(i for i, bar in enumerate(bars) if bar.interval == 240)
    bars[index] = replace(bars[index], c=bars[index].c + Decimal("0.01"))

    assert any("re-aggregated" in problem for problem in check_bars(bars, TICKS))


def test_detects_open_time_off_the_interval_boundary():
    bars = _dataset()
    bars[3] = replace(bars[3], t=bars[3].t + 60)

    assert any("boundary" in problem for problem in check_bars(bars, TICKS))


def test_feasibility_passes_when_every_regime_reaches_its_target():
    series = patterned_series(assets=2, cycles=10, up=1.06, down=0.96)
    params = Params(warmup_weeks=2, eval_weeks=4, candidates=30, targets=PATTERN_TARGETS)

    result = feasibility(series, list(series), params, seed=1, min_weeks=5)

    assert result["passed"] is True
    assert result["label_weeks"] == {UP: 10, FLAT: 10, DOWN: 10}
    assert all(0.0 < rate < 1.0 for rate in result["hit_rates"].values())


def test_feasibility_reports_a_pool_too_short_for_one_dataset_as_a_failure():
    series = patterned_series(assets=2, cycles=10, up=1.06, down=0.96)

    result = feasibility(series, list(series), Params(warmup_weeks=20, eval_weeks=20), seed=1, min_weeks=1)

    assert result["passed"] is False and "ran out" in result["reason"]


def test_feasibility_fails_on_too_few_weeks_or_unreachable_targets():
    series = patterned_series(assets=2, cycles=10, up=1.06, down=0.96)

    few = feasibility(series, list(series), Params(warmup_weeks=2, eval_weeks=4), seed=1, min_weeks=11)
    unreachable = feasibility(series, list(series),
                              Params(warmup_weeks=2, eval_weeks=4, candidates=20, targets=UNREACHABLE),
                              seed=1, min_weeks=5)

    assert few["passed"] is False and "미만" in few["reason"]
    assert unreachable["passed"] is False and unreachable["hit_rates"][BULL] == 0.0


def test_ks_statistic():
    assert ks_statistic([1.0, 2.0, 3.0], [1.0, 2.0, 3.0]) == 0.0
    assert ks_statistic([1.0, 2.0], [3.0, 4.0]) == 1.0


def test_compare_allows_the_gap_real_data_has_with_itself():
    steady = [0.0, 1.0, 2.0, 3.0, 0.0, 1.0, 2.0, 3.0]
    shifted = [0.0, 1.0, 2.0, 3.0, 10.0, 11.0, 12.0, 13.0]

    assert halves_distance(steady) == 0.0 and halves_distance(shifted) == 1.0
    assert compare(steady, [0.0, 1.0, 2.0, 3.0])["passed"] is True
    assert compare(steady, [10.0, 11.0])["passed"] is False
    result = compare(shifted, [10.0, 11.0])
    assert (result["ks"], result["tolerance"], result["passed"]) == (0.5, 1.0, True)


def test_compare_reports_the_shape_of_both_sides():
    real = [float(value) for value in range(100)]

    result = compare(real, [0.0, 50.0, 99.0])

    assert result["real_shape"] == {"p10": 10.0, "p25": 25.0, "p75": 75.0, "p90": 90.0, "mean_abs": 49.5}
    assert result["generated_shape"]["p90"] == 99.0
    assert result["generated_shape"]["mean_abs"] == pytest.approx(49.666667)


def test_window_metrics():
    alternating = {"a": weekly_track("KRW-A", MONDAY, [100.0, 110.0, 99.0, 108.9, 98.01, 98.01])}
    steady = {"a": weekly_track("KRW-A", MONDAY, [100.0, 110.0, 121.0, 133.1, 146.41, 146.41])}
    dip = {"a": weekly_track("KRW-A", MONDAY, [100.0, 100.0, 50.0, 100.0, 100.0])}
    four = [MONDAY + i * WEEK for i in range(1, 5)]

    assert weekly_autocorr(alternating, ["a"], four, WEEK) == pytest.approx(-1.0, abs=1e-6)
    assert monthly_vol(steady, ["a"], four, WEEK) == pytest.approx(0.0, abs=1e-12)
    assert max_drawdown(dip, ["a"], four[:3], WEEK) == pytest.approx(0.5)


def test_seam_values_measure_volatility_and_volume_jumps():
    boundary = MONDAY + WEEK
    before = [(boundary - (24 - i) * HOUR, 100.0 if i % 2 == 0 else 101.0, 10.0) for i in range(24)]
    after = [(boundary + i * HOUR, 100.0 if i % 2 == 0 else 102.0, 20.0) for i in range(24)]

    values = seam_values({"a": before + after}, [boundary])

    assert values["volume_jump"] == [pytest.approx(math.log(2))]
    assert values["vol_jump"][0] == pytest.approx(math.log(2), abs=0.05)


def test_seam_values_come_out_in_boundary_order():
    first, second = MONDAY + WEEK, MONDAY + WEEK + 48 * HOUR
    volumes = [10.0] * 24 + [20.0] * 24 + [5.0] * 24 + [20.0] * 24
    rows = [(first - 24 * HOUR + i * HOUR, 100.0, volume) for i, volume in enumerate(volumes)]

    values = seam_values({"a": rows, "b": list(rows)}, [first, second])

    assert values["volume_jump"] == pytest.approx([math.log(2), math.log(2), math.log(4), math.log(4)])


def test_stage2_runs_every_metric_and_bar_checks():
    series = random_walk_series(assets=3, weeks=40, seed=0)

    result = stage2(series, list(series), Params(warmup_weeks=2, eval_weeks=3), seed=1, generated=30, seam_datasets=3)

    assert result["violations"] == 0
    assert set(result["metrics"]) == {"weekly_autocorr", "monthly_vol", "max_drawdown", "vol_jump", "volume_jump"}
    assert result["real_windows"] == 34
    assert isinstance(result["passed"], bool)
    assert all("tolerance" in metric for metric in result["metrics"].values())
    assert result["seam"] == {"alpha": 0.5, "hours": 24}


def test_stage2_records_the_seam_matching_it_measured():
    series = random_walk_series(assets=2, weeks=20, seed=2)

    result = stage2(series, list(series), Params(warmup_weeks=2, eval_weeks=2), seed=1, generated=10,
                    seam_datasets=2, seam_alpha=1.0, seam_hours=12)

    assert result["seam"] == {"alpha": 1.0, "hours": 12}
    assert result["violations"] == 0
