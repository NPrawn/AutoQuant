"""Score table for a local contest run (ADR-0002, ADR-0003).

Scores are absolute: the return is measured against the starting capital and the drawdown against holding the
market at the same exposure, never against the other submissions. Only the ranking is relative, so a score is
final as soon as the four backtests finish. Same rules as backend ContestScoreCalculator.

Every scale constant is a multiple. Percentage points would mean different things in different rounds because
the market return moves.
"""

import csv
import math
from datetime import datetime

GENERAL = "GENERAL"
REGIMES = ("BULL", "BEAR", "SIDEWAYS")
MIN_EXPOSURE = 0.30
RETURN_WEIGHT = 0.5
RETURN_MULTIPLE = 3.0  # 자산이 3배가 되면 100점
RETURN_FLOOR = -200.0  # 자산이 9분의 1 아래인 구간이라 순위에 닿지 않는다
REGIME_MULTIPLE = 2.0  # 같은 비중으로 시장을 든 경우의 두 배로 빠지면 0점
NO_EXPOSURE_REGIME_SCORE = 50.0  # 그 장세에서 아무것도 사지 않은 경우. 식의 극한이다
TIMEFRAMES = {60: "1H", 240: "4H", 1440: "1D"}


def clamp(value: float) -> float:
    return min(max(value, 0.0), 100.0)


def return_score(cumulative_return: float) -> float:
    """본전이 0점, 자산 3배가 100점이다. 위로는 갇히지 않고 아래로만 -200점에서 자른다."""
    growth = 1 + cumulative_return
    if growth <= 0:
        return RETURN_FLOOR
    return max(RETURN_FLOOR, 100 * math.log(growth) / math.log(RETURN_MULTIPLE))


def regime_score(mdd: float, exposure: float, market_mdd: float) -> float:
    """같은 비중으로 시장을 든 경우와 같으면 50점, 절반만 빠지면 75점, 두 배면 0점이다.

    투자 비중으로 나누는 것이 핵심이다. 덜 사면 낙폭도 그만큼 작아지므로, 나누지 않으면 덜 사는 것만으로
    점수가 오른다. 비중이 0이면 50점이다 — 노출 x 로 시장을 그대로 들면 낙폭도 x 배라 비율이 x 와 무관하게
    1 이므로, 0 은 이 식의 극한이지 예외가 아니다.
    """
    if exposure <= 0 or market_mdd <= 0:
        return NO_EXPOSURE_REGIME_SCORE
    return clamp(100 * (1 - (mdd / (exposure * market_mdd)) / REGIME_MULTIPLE))


def _epoch(text: str) -> int:
    return int(datetime.fromisoformat(text.replace("Z", "+00:00")).timestamp())


def market_drawdowns(csv_path: str, period_start: str, period_end: str) -> dict[str, float]:
    """평가 구간 균등 보유 포트폴리오의 최대 낙폭을 봉 간격별로 잰다. 국면 점수의 기준이다 (ADR-0003).

    엔진은 전략 봉이 닫힐 때마다 자산을 기록하므로 1H·4H·1D 를 따로 잰다. 종목마다 평가 시작 전 마지막 종가를
    기준가로 두고, 봉이 빈 종목은 직전 종가를 쓴다. 고점은 시작 자산에서 출발한다.
    """
    start, end = _epoch(period_start), _epoch(period_end)
    bases: dict[int, dict[str, tuple[int, float]]] = {minutes: {} for minutes in TIMEFRAMES}
    closes: dict[int, dict[int, dict[str, float]]] = {minutes: {} for minutes in TIMEFRAMES}
    with open(csv_path, encoding="utf-8", newline="") as file:
        for row in csv.DictReader(file):
            minutes = int(row["interval_minutes"])
            closed = _epoch(row["open_time"]) + minutes * 60
            close = float(row["close_price"])
            if closed <= start:
                previous = bases[minutes].get(row["asset_id"])
                if previous is None or previous[0] < closed:
                    bases[minutes][row["asset_id"]] = (closed, close)
            elif closed <= end:
                closes[minutes].setdefault(closed, {})[row["asset_id"]] = close

    drawdowns = {}
    for minutes, timeframe in TIMEFRAMES.items():
        if not bases[minutes]:
            raise ValueError(f"{csv_path}: no {timeframe} close before {period_start}")
        base = {asset: close for asset, (_, close) in bases[minutes].items()}
        last = dict(base)
        peak, worst = 1.0, 0.0
        for closed in sorted(closes[minutes]):
            last.update(closes[minutes][closed])
            value = sum(last[asset] / base[asset] for asset in base) / len(base)
            peak = max(peak, value)
            worst = max(worst, 1 - value / peak)
        drawdowns[timeframe] = worst
    return drawdowns


def percentiles(values: dict[str, float], higher_is_better: bool) -> dict[str, float]:
    """(n - rank) / (n - 1) * 100 with rank 1 = best; ties share their average rank."""
    ordered = sorted(values, key=lambda key: values[key], reverse=higher_is_better)
    n = len(ordered)
    if n == 1:
        return {ordered[0]: 100.0}
    ranks: dict[str, float] = {}
    i = 0
    while i < n:
        j = i
        while j + 1 < n and values[ordered[j + 1]] == values[ordered[i]]:
            j += 1
        for key in ordered[i:j + 1]:
            ranks[key] = (i + j) / 2 + 1
        i = j + 1
    return {key: (n - rank) / (n - 1) * 100 for key, rank in ranks.items()}


def disqualification(runs: dict[str, dict]) -> str:
    """점수를 낼 수 없거나(백테스트 미완료) 현금만 들고 시장과 같은 점수를 받는 경우(투자 비중 미달)만 실격이다.

    크게 잃거나 깊이 빠진 전략은 실격 대신 점수가 내려간다. 수익률 점수는 음수까지 간다 (ADR-0003).
    """
    for kind in (GENERAL, *REGIMES):
        run = runs.get(kind)
        if run is None or run["status"] != "COMPLETED":
            return f"{kind} 백테스트 미완료"
    if runs[GENERAL]["exposure"] < MIN_EXPOSURE:
        return f"평균 투자 비중 {runs[GENERAL]['exposure']:.1%} < 30%"
    return ""


def score(results: dict[str, dict[str, dict]]) -> list[dict]:
    """results[strategy][kind] = {"status", "return", "mdd", "exposure", "market_mdd"}. Qualified rows first.

    market_mdd is that dataset's market drawdown on the strategy's timeframe (market_drawdowns).
    """
    reasons = {name: disqualification(runs) for name, runs in results.items()}
    qualified = [name for name, reason in reasons.items() if not reason]
    rows = []
    if qualified:
        for name in qualified:
            returns = return_score(results[name][GENERAL]["return"])
            per_regime = [regime_score(results[name][kind]["mdd"], results[name][kind]["exposure"],
                                       results[name][kind]["market_mdd"])
                          for kind in REGIMES]
            stability = 0.6 * sum(per_regime) / len(per_regime) + 0.4 * min(per_regime)
            rows.append({"strategy": name, "return_score": returns, "stability_score": stability,
                         "total": RETURN_WEIGHT * returns + (1 - RETURN_WEIGHT) * stability,
                         "disqualified": ""})
        rows.sort(key=lambda row: row["total"], reverse=True)
    rows += [{"strategy": name, "return_score": None, "stability_score": None, "total": None, "disqualified": reason}
             for name, reason in reasons.items() if reason]
    return rows
