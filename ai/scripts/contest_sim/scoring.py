"""Score table for a local contest run (ADR-0002, ADR-0003)."""

GENERAL = "GENERAL"
REGIMES = ("BULL", "BEAR", "SIDEWAYS")
MIN_RETURN = -0.30
MAX_MDD = 0.50
MIN_EXPOSURE = 0.30
RETURN_WEIGHT = 0.5


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


def score(results: dict[str, dict[str, dict]]) -> list[dict]:
    """results[strategy][kind] = {"status", "return", "mdd", "exposure"}. Qualified rows first, best total first."""
    reasons = {name: disqualification(runs) for name, runs in results.items()}
    qualified = [name for name, reason in reasons.items() if not reason]
    rows = []
    if qualified:
        returns = percentiles({name: results[name][GENERAL]["return"] for name in qualified}, True)
        stability_by_regime = {
            kind: percentiles({name: results[name][kind]["mdd"] for name in qualified}, False) for kind in REGIMES
        }
        for name in qualified:
            per_regime = [stability_by_regime[kind][name] for kind in REGIMES]
            stability = 0.6 * sum(per_regime) / len(per_regime) + 0.4 * min(per_regime)
            rows.append({"strategy": name, "return_score": returns[name], "stability_score": stability,
                         "total": RETURN_WEIGHT * returns[name] + (1 - RETURN_WEIGHT) * stability,
                         "disqualified": ""})
        rows.sort(key=lambda row: row["total"], reverse=True)
    rows += [{"strategy": name, "return_score": None, "stability_score": None, "total": None, "disqualified": reason}
             for name, reason in reasons.items() if reason]
    return rows
