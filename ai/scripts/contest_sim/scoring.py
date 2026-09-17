"""Score table for a local contest run (ADR-0002, ADR-0003).

Scores are absolute: each submission is measured against the round's market (equal-weight hold return and
drawdown) and the disqualification limits, never against the other submissions. Only the ranking is relative, so a score is final as soon as the
four backtests finish. Same rules as backend ContestScoreCalculator.
"""

import csv
from datetime import datetime

GENERAL = "GENERAL"
REGIMES = ("BULL", "BEAR", "SIDEWAYS")
MIN_RETURN = -0.30
MAX_MDD = 0.50
MIN_EXPOSURE = 0.30
RETURN_WEIGHT = 0.5
RETURN_SPAN = 0.30  # 시장 대비 -30%가 0점, +30%가 100점
REGIME_SPAN = 0.30  # 시장 낙폭보다 30%p 더 빠지면 0점, 30%p 덜 빠지면 100점
TIMEFRAMES = {60: "1H", 240: "4H", 1440: "1D"}


def clamp(value: float) -> float:
    return min(max(value, 0.0), 100.0)


def return_score(cumulative_return: float, market_return: float) -> float:
    """시장과 같으면 50점. 시장 대비 +30%면 100점, -30%면 0점이다."""
    return clamp(((cumulative_return - market_return) + RETURN_SPAN) / (2 * RETURN_SPAN) * 100)


def regime_score(mdd: float, market_mdd: float) -> float:
    """그 장세의 시장 낙폭과 같으면 50점. 시장보다 30%p 덜 빠지면 100점, 30%p 더 빠지면 0점이다."""
    return clamp(((market_mdd - mdd) + REGIME_SPAN) / (2 * REGIME_SPAN) * 100)


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
    for kind in (GENERAL, *REGIMES):
        run = runs.get(kind)
        if run is None or run["status"] != "COMPLETED":
            return f"{kind} 백테스트 미완료"
        if run["return"] < MIN_RETURN:
            return f"{kind} 수익률 {run['return']:.1%} < -30%"
        if run["mdd"] > MAX_MDD:
            return f"{kind} 최대 낙폭 {run['mdd']:.1%} > 50%"
    if runs[GENERAL]["exposure"] < MIN_EXPOSURE:
        return f"평균 투자 비중 {runs[GENERAL]['exposure']:.1%} < 30%"
    return ""


def score(results: dict[str, dict[str, dict]], market_return: float) -> list[dict]:
    """results[strategy][kind] = {"status", "return", "mdd", "exposure", "market_mdd"}. Qualified rows first.

    market_return is the general dataset's equal-weight hold return, which carries the round's difficulty.
    market_mdd is that dataset's market drawdown on the strategy's timeframe (market_drawdowns).
    """
    reasons = {name: disqualification(runs) for name, runs in results.items()}
    qualified = [name for name, reason in reasons.items() if not reason]
    rows = []
    if qualified:
        for name in qualified:
            returns = return_score(results[name][GENERAL]["return"], market_return)
            per_regime = [regime_score(results[name][kind]["mdd"], results[name][kind]["market_mdd"])
                          for kind in REGIMES]
            stability = 0.6 * sum(per_regime) / len(per_regime) + 0.4 * min(per_regime)
            rows.append({"strategy": name, "return_score": returns, "stability_score": stability,
                         "total": RETURN_WEIGHT * returns + (1 - RETURN_WEIGHT) * stability,
                         "disqualified": ""})
        rows.sort(key=lambda row: row["total"], reverse=True)
    rows += [{"strategy": name, "return_score": None, "stability_score": None, "total": None, "disqualified": reason}
             for name, reason in reasons.items() if reason]
    return rows
