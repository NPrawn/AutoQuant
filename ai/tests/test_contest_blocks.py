import csv
import random

import pytest

from contest.blocks import (
    DAY, DOWN, FLAT, HOUR, UP, Week, build_pool, choose_window, first_week_start, label_weeks,
    load_source, parse_time, real_windows, transition_matrix,
)
from tests.contest_fixtures import MONDAY, SOURCE_COLUMNS, WEEK, weekly_track, write_source_csv


def test_parse_time_accepts_psql_and_iso_offsets():
    assert parse_time("2018-01-01 00:00:00+00") == MONDAY
    assert parse_time("2018-01-01T00:00:00Z") == MONDAY
    assert parse_time("2018-01-01 09:00:00+09") == MONDAY


def test_parse_time_rejects_time_without_offset():
    with pytest.raises(ValueError):
        parse_time("2018-01-01 00:00:00")


def test_load_source_filters_assets_and_sorts_candles(tmp_path):
    path = tmp_path / "source.csv"
    with open(path, "w", encoding="utf-8", newline="") as file:
        writer = csv.writer(file)
        writer.writerow(SOURCE_COLUMNS)
        writer.writerow(["KRW-A", "a", "2018-01-01 01:00:00+00", "2", "2", "2", "2", "1", "2"])
        writer.writerow(["KRW-A", "a", "2018-01-01 00:00:00+00", "1", "1", "1", "1", "1", "1"])
        writer.writerow(["KRW-B", "b", "2018-01-01 00:00:00+00", "5", "5", "5", "5", "1", "5"])

    series = load_source(str(path), {"a"})

    assert list(series) == ["a"]
    assert series["a"].market == "KRW-A"
    assert series["a"].times == [MONDAY, MONDAY + HOUR]
    assert series["a"].last_close_before(MONDAY + HOUR) == 1.0


def test_load_source_reads_gzip(tmp_path):
    path = str(tmp_path / "source.csv.gz")
    write_source_csv(path, {"a": weekly_track("KRW-A", MONDAY, [100.0])})

    assert len(load_source(path)["a"].candles) == 7 * 24


def test_first_week_start_aligns_to_weekday():
    assert first_week_start(MONDAY, 0) == MONDAY
    assert first_week_start(MONDAY + 1, 0) == MONDAY + WEEK
    assert first_week_start(MONDAY, 3) == MONDAY + 3 * DAY


def test_build_pool_needs_a_candle_before_and_after_each_week():
    series = {
        "a": weekly_track("KRW-A", MONDAY, [1.0, 2.0, 3.0, 4.0, 5.0]),
        "b": weekly_track("KRW-B", MONDAY + WEEK, [1.0, 2.0, 3.0, 4.0]),
    }

    assert build_pool(series, ["a", "b"], weekday=0) == [MONDAY + 2 * WEEK, MONDAY + 3 * WEEK]


def test_build_pool_keeps_only_weeks_inside_the_window():
    series = {"a": weekly_track("KRW-A", MONDAY, [1.0] * 8)}

    pool = build_pool(series, ["a"], weekday=0, window=(MONDAY + 2 * WEEK, MONDAY + 5 * WEEK))

    assert pool == [MONDAY + 2 * WEEK, MONDAY + 3 * WEEK, MONDAY + 4 * WEEK]


def test_choose_window_picks_a_window_where_enough_assets_trade_throughout():
    series = {
        "a": weekly_track("KRW-A", MONDAY, [1.0] * 12),
        "b": weekly_track("KRW-B", MONDAY + 4 * WEEK, [1.0] * 8),
        "c": weekly_track("KRW-C", MONDAY + 8 * WEEK, [1.0] * 4),
    }
    starts = set()

    for seed in range(20):
        (start, end), universe = choose_window(series, ["a", "b", "c"], 0, 7, 4, 2, random.Random(seed))
        assert universe == ["a", "b"] and end == start + 4 * WEEK
        starts.add((start - MONDAY) // WEEK)

    assert starts == {5, 6, 7}
    with pytest.raises(ValueError):
        choose_window(series, ["a", "b", "c"], 0, 7, 4, 3, random.Random(0))


def test_label_weeks_splits_equal_weight_returns_into_terciles():
    closes = [100.0, 110.0, 99.0, 100.0, 120.0, 60.0, 63.0, 64.0, 80.0, 80.0]
    series = {"a": weekly_track("KRW-A", MONDAY, closes)}
    pool = build_pool(series, ["a"], weekday=0)

    labels = {(week.start - MONDAY) // WEEK: week.label for week in label_weeks(series, ["a"], pool, WEEK)}

    assert labels == {1: FLAT, 2: DOWN, 3: FLAT, 4: UP, 5: DOWN, 6: FLAT, 7: FLAT, 8: UP}


def test_transition_matrix_counts_only_consecutive_weeks():
    weeks = [
        Week(0, UP, 0.0), Week(WEEK, UP, 0.0), Week(2 * WEEK, DOWN, 0.0),
        Week(3 * WEEK, DOWN, 0.0), Week(5 * WEEK, UP, 0.0), Week(6 * WEEK, FLAT, 0.0),
    ]

    matrix = transition_matrix(weeks, WEEK)

    assert matrix[UP] == pytest.approx({UP: 1 / 3, FLAT: 1 / 3, DOWN: 1 / 3})
    assert matrix[DOWN] == pytest.approx({UP: 0.0, FLAT: 0.0, DOWN: 1.0})
    assert matrix[FLAT] == pytest.approx({UP: 1 / 3, FLAT: 1 / 3, DOWN: 1 / 3})


def test_real_windows_slide_over_consecutive_runs():
    weeks = [Week(i * WEEK, FLAT, 0.0) for i in (0, 1, 2, 3, 5, 6)]

    windows = real_windows(weeks, 3, WEEK)

    assert [[week.start for week in window] for window in windows] == [
        [0, WEEK, 2 * WEEK],
        [WEEK, 2 * WEEK, 3 * WEEK],
    ]
