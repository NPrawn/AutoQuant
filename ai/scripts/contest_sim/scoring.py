"""Score table for a local contest run (ADR-0002, ADR-0003).

Scores are absolute: each submission is measured against the round's market return and the disqualification
limits, never against the other submissions. Only the ranking is relative, so a score is final as soon as the
four backtests finish. Same rules as backend ContestScoreCalculator.
"""

GENERAL = "GENERAL"
REGIMES = ("BULL", "BEAR", "SIDEWAYS")
MIN_RETURN = -0.30
MAX_MDD = 0.50
MIN_EXPOSURE = 0.30
RETURN_WEIGHT = 0.5
RETURN_SPAN = 0.30  # 시장 대비 -30%가 0점, +30%가 100점


def clamp(value: float) -> float:
    return min(max(value, 0.0), 100.0)


def return_score(cumulative_return: float, market_return: float) -> float:
    """시장과 같으면 50점. 시장 대비 +30%면 100점, -30%면 0점이다."""
    return clamp(((cumulative_return - market_return) + RETURN_SPAN) / (2 * RETURN_SPAN) * 100)


def regime_score(mdd: float) -> float:
    """구간 최대 낙폭을 실격선 50%로 잰다. 낙폭 0%면 100점, 50%면 0점이다."""
    return clamp((1 - mdd / MAX_MDD) * 100)


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
    """results[strategy][kind] = {"status", "return", "mdd", "exposure"}. Qualified rows first, best total first.

    market_return is the general dataset's equal-weight hold return, which carries the round's difficulty.
    """
    reasons = {name: disqualification(runs) for name, runs in results.items()}
    qualified = [name for name, reason in reasons.items() if not reason]
    rows = []
    if qualified:
        for name in qualified:
            returns = return_score(results[name][GENERAL]["return"], market_return)
            per_regime = [regime_score(results[name][kind]["mdd"]) for kind in REGIMES]
            stability = 0.6 * sum(per_regime) / len(per_regime) + 0.4 * min(per_regime)
            rows.append({"strategy": name, "return_score": returns, "stability_score": stability,
                         "total": RETURN_WEIGHT * returns + (1 - RETURN_WEIGHT) * stability,
                         "disqualified": ""})
        rows.sort(key=lambda row: row["total"], reverse=True)
    rows += [{"strategy": name, "return_score": None, "stability_score": None, "total": None, "disqualified": reason}
             for name, reason in reasons.items() if reason]
    return rows
