"""The verification-only simulator: does it rank strategies the way the market moved?"""

import pytest

from contest.simulate import ARCHETYPES, Strategy, ranks, run, type_ranks
from tests.contest_fixtures import MONDAY, WEEK, weekly_track


def _series(closes: dict[str, list[float]]) -> dict:
    return {asset: weekly_track(f"KRW-{asset.upper()}", MONDAY, values) for asset, values in closes.items()}


def test_holding_a_rising_market_earns_its_return_less_one_trade():
    series = _series({"a": [100.0, 110.0, 121.0, 121.0]})
    hold = next(strategy for strategy in ARCHETYPES if strategy.factor == "HOLD")

    result = run(series, ["a"], MONDAY, MONDAY + 3 * WEEK, hold)

    # 21% over two weeks, minus the 0.15% it costs to get in
    assert result == pytest.approx(0.21 * (1 - 0.0015), rel=0.01)


def test_momentum_follows_the_asset_that_ran():
    series = _series({"runner": [100.0] * 4 + [200.0] * 4, "flat": [100.0] * 8})
    momentum = Strategy("momentum", "추세추종", "RETURN", 7, 1)

    result = run(series, ["runner", "flat"], MONDAY, MONDAY + 7 * WEEK, momentum)

    assert result > 0


def test_ranks_and_type_ranks_cover_every_strategy():
    series = _series({"a": [100.0, 105.0, 110.0, 108.0, 115.0, 115.0] * 20,
                      "b": [100.0, 98.0, 96.0, 99.0, 94.0, 94.0] * 20})

    placings = ranks(series, ["a", "b"], MONDAY, MONDAY + 110 * WEEK)

    assert sorted(placings.values()) == list(range(1, len(ARCHETYPES) + 1))
    assert set(type_ranks(placings)) == {"추세추종", "평균회귀", "저변동성", "거래량", "보유"}
