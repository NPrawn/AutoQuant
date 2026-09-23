import csv
import json

from contest.__main__ import main
from contest.sampling import KINDS, Params
from contest.stitch import LOAD_COLUMNS
from tests.contest_fixtures import (
    MONDAY, patterned_series, random_walk_series, weekly_track, write_source_csv,
)


def _write(tmp_path, series) -> tuple[str, str]:
    source = str(tmp_path / "source.csv.gz")
    write_source_csv(source, series)
    universe = tmp_path / "universe.txt"
    universe.write_text("".join(f"{asset}\n" for asset in series), encoding="utf-8")
    return source, str(universe)


def test_build_writes_four_load_csvs_and_week_list(tmp_path, capsys):
    # up·down = 1, so 4 chained weeks return +22%, 0% or -18%: every default regime target is reachable
    source, universe = _write(tmp_path, patterned_series(assets=2, cycles=10, up=1 / 0.82, down=0.82))
    out = tmp_path / "datasets"

    code = main(["build", "--source", source, "--universe", universe, "--seed", "7", "--out", str(out),
                 "--mode", "stitch", "--warmup-weeks", "2", "--eval-weeks", "4", "--candidates", "50",
                 "--window-weeks", "24", "--min-assets", "2"])

    assert code == 0
    for kind in KINDS:
        with open(out / f"{kind}.csv", encoding="utf-8", newline="") as file:
            rows = list(csv.reader(file))
        assert rows[0] == LOAD_COLUMNS
        assert len(rows) - 1 == 2 * 6 * (168 + 42 + 7)
    weeks = json.loads((out / "weeks.json").read_text(encoding="utf-8"))
    assert list(weeks["datasets"]) == list(KINDS)
    for kind, (low, high) in Params().targets.items():
        assert low <= weeks["datasets"][kind]["evaluation_return"] <= high
        assert low <= weeks["datasets"][kind]["rebalanced_return"] <= high
        assert len(weeks["datasets"][kind]["weeks"]) == 6
    window = weeks["window"]
    assert all(window["start"] <= week["source_start"] < window["end"]
               for kind in KINDS for week in weeks["datasets"][kind]["weeks"])
    assert weeks["markets"] == {"asset-0": "KRW-A0", "asset-1": "KRW-A1"}
    assert weeks["period_start"] == "2030-01-21T00:00:00Z"
    assert json.loads(capsys.readouterr().out)["max_factor_period"] == {"1H": 335, "4H": 83, "1D": 13}


def test_check_reports_both_stages_and_exit_code(tmp_path, capsys):
    source, universe = _write(tmp_path, random_walk_series(assets=3, weeks=40, seed=0))
    out = tmp_path / "check.json"

    code = main(["check", "--source", source, "--universe", universe, "--seed", "1", "--out", str(out),
                 "--min-weeks", "5", "--warmup-weeks", "2", "--eval-weeks", "3", "--candidates", "10",
                 "--generated", "30", "--seam-datasets", "2", "--window-weeks", "0", "--mode", "stitch"])

    report = json.loads(out.read_text(encoding="utf-8"))
    assert set(report) == {"window", "universe", "stage1", "stage2", "passed"}
    assert report["window"] is None and len(report["universe"]) == 3
    assert code == (0 if report["passed"] else 1)
    assert json.loads(capsys.readouterr().out) == report


def test_build_is_byte_identical_for_the_same_seed(tmp_path):
    source, universe = _write(tmp_path, patterned_series(assets=2, cycles=10, up=1 / 0.82, down=0.82))
    args = ["build", "--source", source, "--universe", universe, "--seed", "7", "--warmup-weeks", "2",
            "--eval-weeks", "4", "--candidates", "50", "--window-weeks", "24", "--min-assets", "2", "--fill-gaps",
            "--mode", "stitch"]

    main([*args, "--out", str(tmp_path / "first")])
    main([*args, "--out", str(tmp_path / "second")])

    for name in [f"{kind}.csv" for kind in KINDS] + ["weeks.json"]:
        assert (tmp_path / "first" / name).read_bytes() == (tmp_path / "second" / name).read_bytes()


def _regime_series(assets: int = 2):
    """Six non-overlapping 3-week windows reaching +25%, -16% and flat. Prices stay on a 50 KRW grid."""
    closes = [1000.0,
              1000.0, 1000.0, 1250.0,
              1250.0, 1250.0, 1050.0,
              1050.0, 1050.0, 1050.0,
              1050.0, 1050.0, 1300.0,
              1300.0, 1300.0, 1100.0,
              1100.0, 1100.0, 1100.0,
              1100.0]
    return {f"asset-{index}": weekly_track(f"KRW-A{index}", MONDAY, closes) for index in range(assets)}


def test_build_real_writes_one_window_per_dataset(tmp_path):
    source, universe = _write(tmp_path, _regime_series())
    out = tmp_path / "datasets"

    code = main(["build", "--source", source, "--universe", universe, "--seed", "3", "--out", str(out),
                 "--warmup-weeks", "1", "--eval-weeks", "2", "--universe-size", "2", "--candidates", "500"])

    assert code == 0
    report = json.loads((out / "windows.json").read_text(encoding="utf-8"))
    assert report["mode"] == "real"
    assert list(report["datasets"]) == list(KINDS)
    spans = []
    for kind in KINDS:
        dataset = report["datasets"][kind]
        assert len(dataset["universe"]) == 2
        spans.append((dataset["source_start"], dataset["source_end"]))
        with open(out / f"{kind}.csv", encoding="utf-8", newline="") as file:
            rows = list(csv.reader(file))
        assert rows[0] == LOAD_COLUMNS
        assert len(rows) - 1 == 2 * 3 * (168 + 42 + 7)
    spans.sort()
    assert all(earlier[1] <= later[0] for earlier, later in zip(spans, spans[1:]))
    for kind, (low, high) in Params().targets.items():
        assert low <= report["datasets"][kind]["evaluation_return"] <= high


def test_check_real_reports_the_three_stages(tmp_path, capsys):
    source, universe = _write(tmp_path, _regime_series())
    out = tmp_path / "check.json"

    code = main(["check", "--source", source, "--universe", universe, "--seed", "3", "--out", str(out),
                 "--warmup-weeks", "1", "--eval-weeks", "2", "--universe-size", "2", "--candidates", "500",
                 "--generated", "30"])

    report = json.loads(out.read_text(encoding="utf-8"))
    assert code == 0 and report["passed"] and report["mode"] == "real"
    assert report["stage1"]["windows"] > 0
    for kind in KINDS:
        dataset = report["datasets"][kind]
        assert dataset["stage2"]["passed"] and dataset["stage2"]["compared"] == 2 * 3 * 7 * 24
        assert dataset["stage3"]["windows"] >= dataset["stage3"]["disclosed_windows"]
    assert json.loads(capsys.readouterr().out) == report
