import csv
import json

import pytest

from contest.blocks import parse_time
from contest.sampling import Params
from contest.stitch import LOAD_COLUMNS
from scripts.contest_sim.fairness import neighbors, ranks, spearman, stage3, stage4
from scripts.contest_sim.run_contest import load_strategies
from scripts.contest_sim.validate import grid_keys, grid_strategies, prepare, report
from tests.contest_fixtures import WEEK, random_walk_series, write_source_csv

CAPS = {"1H": 839, "4H": 209, "1D": 34}
STRATEGIES = "scripts/contest_sim/strategies.json"

CONTEST = ["equal-weight-hold", "momentum-7d", "momentum-24h", "rsi-reversion", "trend-filter",
           "low-volatility", "volume-surge", "all-in-one", "cash-only", "drawdown-dip"]
TREND = ("momentum-7d", "momentum-24h", "trend-filter", "all-in-one")


def _completed(value: float) -> dict:
    return {"status": "COMPLETED", "return": value}


def _grid() -> dict[str, tuple[int, int, str]]:
    return {f"g-{p}-{t}-{r}": (p, t, r) for p in (24, 72, 168) for t in (3, 5) for r in ("DAILY", "WEEKLY")}


def test_ranks_share_ties_and_put_failed_runs_last():
    runs = {"a": _completed(0.2), "b": _completed(0.1), "c": _completed(0.1),
            "d": {"status": "FAILED", "return": None}}

    assert ranks(runs) == pytest.approx({"a": 1.0, "b": 2.5, "c": 2.5, "d": 4.0})


def test_spearman_follows_order_and_is_zero_for_all_ties():
    first = {"a": 1.0, "b": 2.0, "c": 3.0}

    assert spearman(first, {"a": 10.0, "b": 20.0, "c": 30.0}) == pytest.approx(1.0)
    assert spearman(first, {"a": 3.0, "b": 2.0, "c": 1.0}) == pytest.approx(-1.0)
    assert spearman(first, {"a": 1.0, "b": 1.0, "c": 1.0}) == 0.0


def test_neighbors_differ_in_exactly_one_setting_by_one_step():
    keys = list(_grid().values())

    assert sorted(neighbors((72, 3, "DAILY"), keys)) == sorted(
        [(24, 3, "DAILY"), (168, 3, "DAILY"), (72, 5, "DAILY"), (72, 3, "WEEKLY")])
    assert (168, 3, "DAILY") not in neighbors((24, 3, "DAILY"), keys)


def test_stage3_flags_a_winner_that_only_wins_on_real_data():
    grid = _grid()
    names = list(grid)
    real = {name: _completed(1 - i * 0.1) for i, name in enumerate(names)}
    memorized = {**real, names[0]: _completed(-5.0)}

    verdict = stage3({"r1": real, "r2": real, "g1": memorized, "g2": memorized}, ["r1", "r2"], ["g1", "g2"], grid)
    steady = stage3({"r1": real, "r2": real, "g1": real, "g2": real}, ["r1", "r2"], ["g1", "g2"], grid)

    assert verdict["overfit"]["strategy"] == names[0]
    assert verdict["overfit"]["drop"] == pytest.approx(100.0)
    assert verdict["robust"]["strategy"] != names[0]
    assert verdict["held_out_real_drop"] == pytest.approx(0.0)
    assert verdict["passed"] is True
    assert steady["overfit"]["drop"] == pytest.approx(0.0) and steady["passed"] is False


def _stage4_results(generated: dict) -> dict:
    same = {name: _completed(1 - i * 0.1) for i, name in enumerate(CONTEST)}
    return {**{f"r{i}": same for i in (1, 2, 3)}, **{f"g{i}": generated for i in (1, 2, 3)},
            **{f"t{i}": same for i in (1, 2, 3)}}


def test_stage4_passes_when_generated_ranks_stay_inside_the_real_range():
    same = {name: _completed(1 - i * 0.1) for i, name in enumerate(CONTEST)}

    verdict = stage4(_stage4_results(same), ["r1", "r2", "r3"], ["g1", "g2", "g3"], ["t1", "t2", "t3"], CONTEST)

    assert verdict["passed"] is True
    assert verdict["luck"]["generated_mean"] == pytest.approx(1.0) == verdict["luck"]["real_mean"]
    assert verdict["failed_backtests"] == {name: 0 for name in ["r1", "r2", "r3", "g1", "g2", "g3", "t1", "t2", "t3"]}


def test_stage4_fails_a_type_the_generated_data_pushes_down():
    same = {name: _completed(1 - i * 0.1) for i, name in enumerate(CONTEST)}
    trend_last = {**same, **{name: _completed(-5.0) for name in TREND}}

    verdict = stage4(_stage4_results(trend_last), ["r1", "r2", "r3"], ["g1", "g2", "g3"], ["t1", "t2", "t3"],
                     CONTEST)

    assert verdict["types"]["추세추종"]["passed"] is False
    assert verdict["passed"] is False


def test_grid_strategies_cover_every_momentum_setting_within_the_cap():
    grid = grid_strategies(load_strategies(STRATEGIES, CAPS))

    assert len(grid) == 24 == len({name for name, _, _ in grid})
    settings = {(s.ranking.period, s.selection.topN, s.rebalance.period.value) for _, _, s in grid}
    assert settings == set(grid_keys().values())
    assert max(s.ranking.period for _, _, s in grid) <= CAPS["1H"]


def test_prepare_writes_real_monday_and_thursday_datasets(tmp_path):
    series = random_walk_series(assets=3, weeks=40, seed=0)
    source = str(tmp_path / "source.csv.gz")
    write_source_csv(source, series)
    universe = tmp_path / "universe.txt"
    universe.write_text("".join(f"{asset}\n" for asset in series), encoding="utf-8")

    manifest = prepare(source, str(universe), 1, str(tmp_path / "data"), Params(warmup_weeks=2, eval_weeks=3),
                       window_weeks=30, min_assets=2)

    names = [f"{group}-{i}" for group in ("real", "gen", "thu") for i in range(1, 6)]
    assert list(manifest["datasets"]) == names
    for name, dataset in manifest["datasets"].items():
        assert dataset["group"] == name.split("-")[0] and len(dataset["weeks"]) == 5
        with open(tmp_path / "data" / f"{name}.csv", encoding="utf-8", newline="") as file:
            assert next(csv.reader(file)) == LOAD_COLUMNS
    real_starts = [parse_time(week) for i in range(1, 6) for week in manifest["datasets"][f"real-{i}"]["weeks"]]
    assert real_starts == [real_starts[0] + i * WEEK for i in range(25)]
    assert manifest["datasets"]["gen-1"]["period_start"] == "2030-01-21T00:00:00Z"
    assert manifest["datasets"]["thu-1"]["period_start"] == "2030-01-17T00:00:00Z"
    assert (tmp_path / "data" / "manifest.json").exists()


def test_report_needs_every_backtest_and_writes_both_stages(tmp_path):
    data = tmp_path / "data"
    data.mkdir()
    datasets = {f"{group}-{i}": {"group": group, "period_start": "2030-02-11T00:00:00Z",
                                 "period_end": "2030-05-13T00:00:00Z"}
                for group in ("real", "gen", "thu") for i in range(1, 6)}
    (data / "manifest.json").write_text(json.dumps(
        {"seed": 1, "window": None, "max_factor_period": CAPS, "datasets": datasets}), encoding="utf-8")
    contest = [name for name, _, _ in load_strategies(STRATEGIES, CAPS)]
    rows = []
    for name, dataset in datasets.items():
        strategies = contest + ([] if dataset["group"] == "thu" else list(grid_keys()))
        rows += [{"dataset": name, "strategy": strategy, "status": "COMPLETED", "return": 1 - i * 0.01}
                 for i, strategy in enumerate(strategies)]
    out = tmp_path / "report.json"

    (data / "results.jsonl").write_text("".join(json.dumps(row) + "\n" for row in rows[1:]), encoding="utf-8")
    with pytest.raises(SystemExit, match="1 backtests missing"):
        report(str(data), STRATEGIES, str(out))

    (data / "results.jsonl").write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
    verdict = report(str(data), STRATEGIES, str(out))

    assert set(verdict) == {"seed", "window", "stage3", "stage4", "passed"}
    assert verdict["stage4"]["passed"] is True and verdict["stage3"]["passed"] is False
    assert json.loads(out.read_text(encoding="utf-8")) == verdict
