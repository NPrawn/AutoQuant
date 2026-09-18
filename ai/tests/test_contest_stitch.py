import csv
from decimal import Decimal

from contest.blocks import HOUR, UP, Candle, Track, Week, parse_time
from contest.stitch import LOAD_COLUMNS, Bar, aggregate, fill_gaps, snap, stitch, tick_table, write_load_csv
from tests.contest_fixtures import MONDAY, WEEK, weekly_track

VIRTUAL = parse_time("2030-01-07T00:00:00Z")
TICKS = {2: Decimal("0.01")}


def test_tick_table_uses_smallest_gap_per_decade():
    track = Track("KRW-A")
    for i, price in enumerate([1.23, 1.25, 1.24, 120.0, 125.0]):
        track.candles.append(Candle(MONDAY + i * HOUR, price, price, price, price, 1.0, price))
    track.finish()

    assert tick_table({"a": track}) == {0: Decimal("0.01"), 2: Decimal("5")}


def test_snap_rounds_half_up_and_borrows_the_nearest_decade():
    table = {0: Decimal("0.01"), 2: Decimal("5")}

    assert snap(1.234, table) == Decimal("1.23")
    assert snap(1.235, table) == Decimal("1.24")
    assert snap(123.0, table) == Decimal("125")
    assert snap(12.34, table) == Decimal("12.3")


def test_fill_gaps_repeats_the_previous_close_with_zero_volume():
    one, two = Decimal("100.00"), Decimal("101.00")
    hourly = [
        Bar("a", 60, VIRTUAL, one, one, one, one, Decimal(2), one * 2),
        Bar("a", 60, VIRTUAL + 3 * HOUR, two, two, two, two, Decimal(1), two),
        Bar("b", 60, VIRTUAL, one, one, one, one, Decimal(1), one),
    ]

    filled, added = fill_gaps(hourly)

    assert added == 2
    a = [bar for bar in filled if bar.asset == "a"]
    assert [bar.t for bar in a] == [VIRTUAL + i * HOUR for i in range(4)]
    assert all(bar.o == bar.h == bar.l == bar.c == one and bar.v == 0 and bar.a == 0 for bar in a[1:3])
    assert len(filled) == 5


def test_stitch_chains_prices_so_each_week_keeps_its_return():
    series = {"a": weekly_track("KRW-A", MONDAY, [100.0, 200.0, 50.0, 400.0, 400.0])}
    sequence = [Week(MONDAY + 3 * WEEK, UP, 0.0), Week(MONDAY + WEEK, UP, 0.0)]

    bars = stitch(series, ["a"], sequence, WEEK, 4, VIRTUAL, TICKS)

    assert len(bars) == 2 * 7 * 24
    assert all(bar.interval == 60 for bar in bars)
    assert bars[0].t == VIRTUAL and bars[0].c == Decimal("400")
    assert bars[168].t == VIRTUAL + WEEK and bars[168].c == Decimal("800")
    assert bars[168].a == Decimal("8000")


def test_stitch_rescales_volume_to_the_recent_generated_level():
    series = {"a": weekly_track("KRW-A", MONDAY, [100.0] * 5, volumes=[10.0, 10.0, 1000.0, 1000.0, 1000.0])}
    sequence = [Week(MONDAY + WEEK, UP, 0.0), Week(MONDAY + 3 * WEEK, UP, 0.0)]

    bars = stitch(series, ["a"], sequence, WEEK, 1, VIRTUAL, TICKS)

    assert abs(bars[0].v - Decimal(10)) < Decimal("1e-9")
    assert abs(bars[168].v - Decimal(10)) < Decimal("1e-9")
    assert abs(bars[168].a - Decimal(1000)) < Decimal("1e-3")


def test_aggregate_follows_rollup_rules():
    closes = ["100.01", "100.05", "99.90", "100.20", "100.30", "100.10", "100.40", "100.35"]
    hourly = [
        Bar("a", 60, VIRTUAL + i * HOUR, Decimal(close), Decimal(close) + 1, Decimal(close) - 1,
            Decimal(close), Decimal(1), Decimal(close))
        for i, close in enumerate(closes)
    ]

    four_hour = aggregate(hourly, 240)

    assert [bar.t for bar in four_hour] == [VIRTUAL, VIRTUAL + 4 * HOUR]
    first = four_hour[0]
    assert (first.o, first.c) == (Decimal("100.01"), Decimal("100.20"))
    assert (first.h, first.l) == (Decimal("101.20"), Decimal("98.90"))
    assert (first.v, first.a) == (Decimal(4), Decimal("400.16"))
    assert aggregate(hourly, 1440)[0].t == VIRTUAL


def test_write_load_csv_matches_minute_candle_columns(tmp_path):
    bar = Bar("a", 60, VIRTUAL, Decimal("1.5"), Decimal("2"), Decimal("1"), Decimal("1.75"),
              Decimal("3"), Decimal("5.25"))
    path = tmp_path / "GENERAL.csv"

    write_load_csv(str(path), [bar])

    with open(path, encoding="utf-8", newline="") as file:
        rows = list(csv.reader(file))
    assert rows[0] == LOAD_COLUMNS
    assert rows[1] == ["a", "60", "2030-01-07T00:00:00Z", "1.5", "2", "1", "1.75", "3", "5.25",
                       "2030-01-07T00:59:59Z"]
