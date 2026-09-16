"""The Flyway seed the generator output turns into (ADR-0016)."""

import csv
import json
import os
import re
import uuid

import pytest

from scripts.contest_sim.export_seed_sql import KINDS, export

ROUND_ID = "0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10"
MARKETS = {"asset-a": "KRW-BTC", "asset-b": "KRW-ETH"}
WEEK_STARTS = [f"2019-10-{7 + 7 * index:02d}T00:00:00Z" for index in range(3)]
LOAD_COLUMNS = [
    "asset_id", "interval_minutes", "open_time", "open_price", "high_price", "low_price",
    "close_price", "trade_volume", "trade_amount", "last_event_time",
]


def _hourly_row(asset: str, hour: int) -> dict:
    """One generated hourly candle. Padding zeros are what write_load_csv emits."""
    return {
        "asset_id": asset,
        "interval_minutes": "60",
        "open_time": f"2030-01-07T{hour:02d}:00:00Z",
        "open_price": "100.000000000000",
        "high_price": "101.500000000000",
        "low_price": "99.000000000000",
        "close_price": "101.000000000000",
        "trade_volume": "2.500000000000000000",
        "trade_amount": "252.50000000",
        "last_event_time": f"2030-01-07T{hour:02d}:59:59Z",
    }


def _write_datasets(directory: str, hours: int = 3, weeks: int = 3) -> dict:
    """A generator output directory: weeks.json plus one CSV per dataset."""
    summary = {
        "seed": 20260915,
        "window": {"start": "2019-10-07T00:00:00Z", "end": "2021-10-04T00:00:00Z"},
        "universe": sorted(MARKETS),
        "markets": MARKETS,
        "period_start": "2030-01-14T00:00:00Z",
        "period_end": "2030-01-28T00:00:00Z",
        "max_factor_period": 839,
        "datasets": {},
    }
    for kind in KINDS:
        summary["datasets"][kind] = {
            "evaluation_return": 0.2389,
            "rebalanced_return": 0.0421,
            "filled_hours": 12,
            "weeks": [{"source_start": start, "label": "UP"} for start in WEEK_STARTS[:weeks]],
        }
        path = os.path.join(directory, f"{kind}.csv")
        with open(path, "w", encoding="utf-8", newline="") as file:
            writer = csv.DictWriter(file, LOAD_COLUMNS, lineterminator="\n")
            writer.writeheader()
            for asset in sorted(MARKETS):
                for hour in range(hours):
                    writer.writerow(_hourly_row(asset, hour))
                # 집계 봉도 같은 CSV 에 있다. 마이그레이션은 1시간봉만 싣는다.
                rolled = _hourly_row(asset, 0) | {"interval_minutes": "240"}
                writer.writerow(rolled)
    with open(os.path.join(directory, "weeks.json"), "w", encoding="utf-8", newline="\n") as file:
        json.dump(summary, file, ensure_ascii=False)
    return summary


def _export(tmp_path, **overrides) -> tuple[str, dict]:
    datasets = tmp_path / "datasets"
    datasets.mkdir(parents=True)
    _write_datasets(str(datasets), **overrides)
    out = tmp_path / "V99__seed.sql"
    report = export(str(datasets), ROUND_ID, "1회차 모의 대회", "2026-09-16T00:00:00Z",
                    "2026-10-16T00:00:00Z", 5, "contest-2026-09-15", str(out))
    return out.read_text(encoding="utf-8"), report


def test_dataset_ids_come_from_the_round_so_a_rerun_writes_the_same_file(tmp_path):
    first, report = _export(tmp_path / "one")
    second, again = _export(tmp_path / "two")

    assert first == second
    assert report["datasets"] == again["datasets"]
    assert report["datasets"]["BULL"] == str(uuid.uuid5(uuid.UUID(ROUND_ID), "BULL"))


def test_round_row_takes_warmup_and_evaluation_weeks_from_the_generated_period(tmp_path):
    sql, _ = _export(tmp_path)

    round_values = sql.split("INSERT INTO contest.contest_round")[1].split(";")[0]
    # 평가 기간 2주, 뽑은 주 3주 → 워밍업 1주
    assert "'READY',\n    5, 1, 2\n" in round_values


def test_only_hourly_rows_are_written_and_higher_intervals_are_aggregated_in_sql(tmp_path):
    sql, report = _export(tmp_path, hours=3)

    assert report["hourly_rows"] == len(KINDS) * len(MARKETS) * 3
    assert sql.count(", 60, ") == len(KINDS)
    assert "       240,\n" in sql and "       1440,\n" in sql
    assert ", 240, " not in sql


def test_hourly_inserts_are_chunked(tmp_path):
    sql, report = _export(tmp_path, hours=400)

    # 종목 2개 × 400시간 = 800행이라 500행 청크 두 개가 나온다
    assert report["hourly_rows"] == len(KINDS) * 800
    assert sql.count("FROM (VALUES") == len(KINDS) * 2


def test_assets_are_joined_by_market_code_and_the_migration_fails_when_one_is_missing(tmp_path):
    sql, _ = _export(tmp_path)

    assert "ON CONFLICT (exchange, exchange_market_code) DO NOTHING" in sql
    assert sql.count("'UPBIT', 'KRW-BTC'") == 1
    assert f"<> {len(MARKETS) * len(KINDS)} THEN" in sql
    assert "RAISE EXCEPTION" in sql


def test_padding_zeros_are_dropped_without_changing_the_value(tmp_path):
    sql, _ = _export(tmp_path)

    values = re.findall(r"\('KRW-BTC', '2030-01-07T00:00:00Z', ([^)]+)\)", sql)[0]
    assert values == "100, 101.5, 99, 101, 2.5, 252.5"


def test_source_weeks_and_market_return_ride_along_for_the_result_screen(tmp_path):
    sql, _ = _export(tmp_path)

    datasets = sql.split("INSERT INTO contest.market_dataset (")[1].split(";")[0]
    assert "0.23890000" in datasets
    assert WEEK_STARTS[0] in datasets
    assert "'contest-2026-09-15'" in datasets


def test_a_csv_asset_missing_from_weeks_json_is_named(tmp_path):
    datasets = tmp_path / "datasets"
    datasets.mkdir()
    _write_datasets(str(datasets))
    stale = datasets / "BULL.csv"
    stale.write_text(stale.read_text(encoding="utf-8").replace("asset-b", "asset-gone"), encoding="utf-8")

    with pytest.raises(KeyError, match="asset-gone"):
        export(str(datasets), ROUND_ID, "1회차", "2026-09-16T00:00:00Z", "2026-10-16T00:00:00Z",
               5, "contest-2026-09-15", str(tmp_path / "out.sql"))
