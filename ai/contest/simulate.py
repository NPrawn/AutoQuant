"""A small portfolio simulator for verification only (DESIGN-CONTEST-DATA-QUALITY 설계 4).

Stage 4 asks whether the windows we picked rank strategy types the way other real windows do. That question is
about ranks, not about won and jeon, so this walks daily closes instead of going through the engine: 44 minutes
of HTTP and queue work becomes seconds. Scores that reach participants always come from the engine.
"""

import statistics
from dataclasses import dataclass

from contest.blocks import DAY, Series

FEE = 0.0005          # 업비트 원화 마켓 기본 수수료
SLIPPAGE = 0.001      # 대회 채점과 같은 10bp


@dataclass(frozen=True)
class Strategy:
    name: str
    kind: str          # 추세추종·평균회귀·저변동성·거래량·보유
    factor: str        # RETURN·VOLATILITY·VOLUME_RATIO·DRAWDOWN·SMA_DISPARITY·HOLD
    period: int        # in days
    top: int
    highest: bool = True   # True 면 큰 값부터, False 면 작은 값부터


ARCHETYPES = (
    Strategy("momentum-7d", "추세추종", "RETURN", 7, 5),
    Strategy("momentum-30d", "추세추종", "RETURN", 30, 5),
    Strategy("momentum-90d", "추세추종", "RETURN", 90, 5),
    Strategy("drawdown-dip", "평균회귀", "DRAWDOWN", 30, 5, highest=False),
    Strategy("below-average", "평균회귀", "SMA_DISPARITY", 20, 5, highest=False),
    Strategy("low-volatility", "저변동성", "VOLATILITY", 30, 5, highest=False),
    Strategy("high-volatility", "저변동성", "VOLATILITY", 30, 5),
    Strategy("volume-surge", "거래량", "VOLUME_RATIO", 20, 5),
    Strategy("equal-weight-hold", "보유", "HOLD", 0, 0),
)


def daily_closes(series: Series, universe: list[str], start: int,
                 end: int) -> tuple[dict[str, list[tuple[int, float]]], dict[str, list[float]]]:
    """One close and one volume per UTC day, taken from the last hourly candle of that day."""
    tracks: dict[str, list[tuple[int, float]]] = {}
    volumes: dict[str, list[float]] = {}
    for asset in universe:
        by_day: dict[int, tuple[float, float]] = {}
        for candle in series[asset].between(start, end):
            day = candle.t - candle.t % DAY
            close, volume = by_day.get(day, (0.0, 0.0))
            by_day[day] = (candle.c, volume + candle.v)
        tracks[asset] = [(day, close) for day, (close, _) in sorted(by_day.items())]
        volumes[asset] = [volume for _, (_, volume) in sorted(by_day.items())]
    return tracks, volumes


def _score(strategy: Strategy, closes: list[float], volumes: list[float], index: int) -> float | None:
    """The factor value on day `index`, or None when the warmup is too short."""
    period = strategy.period
    if strategy.factor == "HOLD":
        return 0.0
    if index < period or period <= 0:
        return None
    window = closes[index - period:index + 1]
    if strategy.factor == "RETURN":
        return window[-1] / window[0] - 1
    if strategy.factor == "VOLATILITY":
        returns = [later / earlier - 1 for earlier, later in zip(window, window[1:]) if earlier > 0]
        return statistics.pstdev(returns) if len(returns) >= 2 else None
    if strategy.factor == "DRAWDOWN":
        return window[-1] / max(window) - 1
    if strategy.factor == "SMA_DISPARITY":
        return window[-1] / statistics.fmean(window) - 1
    if strategy.factor == "VOLUME_RATIO":
        recent = volumes[index - period:index + 1]
        average = statistics.fmean(recent[:-1]) if len(recent) > 1 else 0.0
        return recent[-1] / average if average > 0 else None
    raise ValueError(f"unknown factor: {strategy.factor}")


def run(series: Series, universe: list[str], start: int, end: int, strategy: Strategy,
        rebalance_days: int = 7) -> float:
    """Cumulative return over [start, end). Holdings are rebuilt every `rebalance_days` from the factor rank."""
    tracks, volumes = daily_closes(series, universe, start, end)
    days = len(next(iter(tracks.values())))
    held: dict[str, float] = {}
    cash = 1.0
    for index in range(days):
        prices = {asset: tracks[asset][index][1] for asset in universe}
        value = cash + sum(units * prices[asset] for asset, units in held.items())
        if index % rebalance_days:
            continue
        if strategy.factor == "HOLD":
            chosen = list(universe) if index == 0 else list(held)
        else:
            scored = [(asset, _score(strategy, [close for _, close in tracks[asset]], volumes[asset], index))
                      for asset in universe]
            ready = [(asset, score) for asset, score in scored if score is not None]
            if not ready:
                continue
            ready.sort(key=lambda row: row[1], reverse=strategy.highest)
            chosen = [asset for asset, _ in ready[:strategy.top]]
        if set(chosen) == set(held) and held:
            continue
        traded = value * (FEE + SLIPPAGE) if held or chosen else 0.0
        value -= traded
        held = {asset: value / len(chosen) / prices[asset] for asset in chosen} if chosen else {}
        cash = 0.0 if chosen else value
    prices = {asset: tracks[asset][-1][1] for asset in universe}
    return cash + sum(units * prices[asset] for asset, units in held.items()) - 1


def ranks(series: Series, universe: list[str], start: int, end: int,
          strategies=ARCHETYPES) -> dict[str, int]:
    """1 is the best return. Ranking is what stage 4 compares across windows."""
    returns = {strategy.name: run(series, universe, start, end, strategy) for strategy in strategies}
    ordered = sorted(returns, key=lambda name: -returns[name])
    return {name: place + 1 for place, name in enumerate(ordered)}


def type_ranks(placings: dict[str, int], strategies=ARCHETYPES) -> dict[str, float]:
    """Average rank per strategy type, the shape ADR-0012 stage 4 compares."""
    kinds: dict[str, list[int]] = {}
    for strategy in strategies:
        kinds.setdefault(strategy.kind, []).append(placings[strategy.name])
    return {kind: statistics.fmean(places) for kind, places in kinds.items()}
