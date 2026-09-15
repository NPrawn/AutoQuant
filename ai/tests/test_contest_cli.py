import csv
import json

from contest.__main__ import main
from contest.sampling import KINDS, Params
from contest.stitch import LOAD_COLUMNS
from tests.contest_fixtures import patterned_series, random_walk_series, write_source_csv


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
                 "--warmup-weeks", "2", "--eval-weeks", "4", "--candidates", "50"])

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
        assert len(weeks["datasets"][kind]["weeks"]) == 6
    assert weeks["period_start"] == "2030-01-21T00:00:00Z"
    assert json.loads(capsys.readouterr().out)["max_factor_period"] == {"1H": 335, "4H": 83, "1D": 13}


def test_check_reports_both_stages_and_exit_code(tmp_path, capsys):
    source, universe = _write(tmp_path, random_walk_series(assets=3, weeks=40, seed=0))
    out = tmp_path / "check.json"

    code = main(["check", "--source", source, "--universe", universe, "--seed", "1", "--out", str(out),
                 "--min-weeks", "5", "--warmup-weeks", "2", "--eval-weeks", "3", "--candidates", "10",
                 "--generated", "30", "--seam-datasets", "2"])

    report = json.loads(out.read_text(encoding="utf-8"))
    assert set(report) == {"stage1", "stage2", "passed"}
    assert code == (0 if report["passed"] else 1)
    assert json.loads(capsys.readouterr().out) == report
