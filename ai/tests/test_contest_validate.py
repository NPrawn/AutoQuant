import pytest

from scripts.contest_sim.fairness import neighbors, ranks, spearman, stage3, stage4

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
    assert verdict["luck"]["generated_mean"] == pytest.approx(1.0)
    assert verdict["failed_backtests"] == {name: 0 for name in ["r1", "r2", "r3", "g1", "g2", "g3", "t1", "t2", "t3"]}


def test_stage4_fails_a_type_the_generated_data_pushes_down():
    same = {name: _completed(1 - i * 0.1) for i, name in enumerate(CONTEST)}
    trend_last = {**same, **{name: _completed(-5.0) for name in TREND}}

    verdict = stage4(_stage4_results(trend_last), ["r1", "r2", "r3"], ["g1", "g2", "g3"], ["t1", "t2", "t3"],
                     CONTEST)

    assert verdict["types"]["추세추종"]["passed"] is False
    assert verdict["passed"] is False
