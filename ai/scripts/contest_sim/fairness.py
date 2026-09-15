"""Stage 3 (memorized strategies) and stage 4 (fairness) verdicts from backtest results (ai/docs/adr/0012).

results[dataset][strategy] = {"status": "COMPLETED" | "FAILED", "return": float | None, ...}
"The range real data has with itself" is the min..max over the real datasets.
"""

import itertools
import statistics

from scripts.contest_sim.scoring import percentiles

TYPES = {
    "추세추종": ("momentum-7d", "momentum-24h", "trend-filter", "all-in-one"),
    "평균회귀": ("rsi-reversion", "drawdown-dip"),
    "저변동성": ("low-volatility",),
    "거래량": ("volume-surge",),
}
WEEKLY = ("momentum-7d", "low-volatility", "drawdown-dip")


def ranks(runs: dict[str, dict]) -> dict[str, float]:
    """Rank by cumulative return, 1 = best, ties share their average rank. A failed run ties for last."""
    completed = [run["return"] for run in runs.values() if run["status"] == "COMPLETED"]
    last = min(completed, default=0.0) - 1
    values = {name: run["return"] if run["status"] == "COMPLETED" else last for name, run in runs.items()}
    n = len(values)
    return {name: n - percentile * (n - 1) / 100 for name, percentile in percentiles(values, True).items()}


def spearman(first: dict[str, float], second: dict[str, float]) -> float:
    """Rank correlation over the same names; 0 when either side is all ties."""
    names = sorted(first)
    a, b = percentiles(first, True), percentiles(second, True)
    try:
        return statistics.correlation([a[name] for name in names], [b[name] for name in names])
    except statistics.StatisticsError:
        return 0.0


def _tables(results: dict[str, dict[str, dict]], datasets: list[str], strategies: list[str]) -> list[dict[str, float]]:
    return [ranks({name: results[dataset][name] for name in strategies}) for dataset in datasets]


def _mean(tables: list[dict[str, float]], names) -> dict[str, float]:
    return {name: statistics.fmean(table[name] for table in tables) for name in names}


def neighbors(key: tuple[int, int, str], keys: list[tuple[int, int, str]]) -> list[tuple[int, int, str]]:
    """Grid keys (period, top N, rebalance) that differ in one setting; a period differs by one step."""
    periods = sorted({other[0] for other in keys})

    def one_step(other: tuple[int, int, str]) -> bool:
        changed = [i for i in range(3) if key[i] != other[i]]
        if len(changed) != 1:
            return False
        return changed[0] != 0 or abs(periods.index(key[0]) - periods.index(other[0])) == 1

    return [other for other in keys if one_step(other)]


def stage3(results: dict[str, dict[str, dict]], real: list[str], generated: list[str],
           grid: dict[str, tuple[int, int, str]]) -> dict:
    names = list(grid)
    n = len(names)
    real_rank = _mean(_tables(results, real, names), names)
    generated_rank = _mean(_tables(results, generated, names), names)

    def percentile(rank: float) -> float:
        return (n - rank) / (n - 1) * 100

    drop = {name: percentile(real_rank[name]) - percentile(generated_rank[name]) for name in names}
    by_key = {key: name for name, key in grid.items()}

    def neighborhood(name: str) -> float:
        return statistics.median(real_rank[by_key[key]] for key in neighbors(grid[name], list(by_key)))

    overfit = min(names, key=lambda name: real_rank[name])
    robust = min((name for name in names if name != overfit), key=neighborhood)
    median_drop = statistics.median(drop.values())

    def held_out_drop(held: str) -> float:
        """Control: pick the winner on the other real datasets and move it to this real one instead."""
        train = [dataset for dataset in real if dataset != held]
        train_rank = _mean(_tables(results, train, names), names)
        best = min(names, key=lambda name: train_rank[name])
        return percentile(train_rank[best]) - percentile(ranks({name: results[held][name] for name in names})[best])

    held_out = statistics.fmean(held_out_drop(held) for held in real) if len(real) > 1 else None

    def row(name: str) -> dict:
        return {"strategy": name, "real_rank": real_rank[name], "generated_rank": generated_rank[name],
                "drop": drop[name]}

    return {
        "overfit": row(overfit),
        "robust": {**row(robust), "neighborhood_rank": neighborhood(robust)},
        "median_drop": median_drop,
        "held_out_real_drop": held_out,
        "rank_correlation": spearman(real_rank, generated_rank),
        "passed": drop[overfit] > drop[robust] and drop[overfit] > median_drop,
    }


def stage4(results: dict[str, dict[str, dict]], real: list[str], generated: list[str], thursday: list[str],
           strategies: list[str]) -> dict:
    real_tables = _tables(results, real, strategies)
    generated_tables = _tables(results, generated, strategies)
    thursday_tables = _tables(results, thursday, strategies)

    def bounds(values: list[float]) -> dict:
        return {"real_min": min(values), "real_max": max(values)}

    types = {}
    for label, members in TYPES.items():
        real_values = [statistics.fmean(table[m] for m in members) for table in real_tables]
        value = statistics.fmean(statistics.fmean(table[m] for m in members) for table in generated_tables)
        types[label] = {**bounds(real_values), "generated": value,
                        "passed": min(real_values) <= value <= max(real_values)}

    real_pairs = [spearman(a, b) for a, b in itertools.combinations(real_tables, 2)]
    generated_mean = statistics.fmean(spearman(a, b) for a, b in itertools.combinations(generated_tables, 2))
    luck = {**bounds(real_pairs), "real_mean": statistics.fmean(real_pairs), "generated_mean": generated_mean,
            "passed": min(real_pairs) <= generated_mean <= max(real_pairs)}

    seams = {}
    for name in WEEKLY:
        real_values = [table[name] for table in real_tables]
        monday = statistics.fmean(table[name] for table in generated_tables)
        value = statistics.fmean(table[name] for table in thursday_tables)
        seams[name] = {**bounds(real_values), "monday": monday, "thursday": value,
                       "passed": min(real_values) <= value <= max(real_values)}

    return {
        "types": types,
        "luck": luck,
        "seam_weekday": seams,
        "failed_backtests": {dataset: sum(run["status"] != "COMPLETED" for run in results[dataset].values())
                             for dataset in [*real, *generated, *thursday]},
        "passed": all(item["passed"] for item in [*types.values(), luck, *seams.values()]),
    }
