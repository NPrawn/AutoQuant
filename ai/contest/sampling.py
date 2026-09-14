"""Pick week sequences for contest markets (ADR-0011 steps 2-4)."""

import random
from dataclasses import dataclass, field

from contest.blocks import DAY, DOWN, FLAT, LABELS, UP, Series, Week, first_week_start, parse_time, period_return

GENERAL, BULL, BEAR, SIDEWAYS = "GENERAL", "BULL", "BEAR", "SIDEWAYS"
KINDS = (GENERAL, BULL, BEAR, SIDEWAYS)
REGIME_LABEL = {BULL: UP, BEAR: DOWN, SIDEWAYS: FLAT}


class GenerationError(RuntimeError):
    pass


@dataclass(frozen=True)
class Params:
    block_days: int = 7
    block_start: int = 0                      # Monday=0 ... Thursday=3
    warmup_weeks: int = 5
    eval_weeks: int = 13
    candidates: int = 1000
    volume_ref_weeks: int = 4
    virtual_from: int = parse_time("2030-01-01T00:00:00Z")
    targets: dict[str, tuple[float, float]] = field(default_factory=lambda: {
        BULL: (0.20, 0.30),
        BEAR: (-0.20, -0.10),
        SIDEWAYS: (-0.03, 0.03),
    })

    @property
    def span(self) -> int:
        return self.block_days * DAY

    @property
    def virtual_start(self) -> int:
        return first_week_start(self.virtual_from, self.block_start)


def max_factor_period(params: Params) -> dict[str, int]:
    """ADR-0013: a factor period must fit inside the warmup candles."""
    hours = params.warmup_weeks * params.block_days * 24
    return {"1H": hours - 1, "4H": hours // 4 - 1, "1D": hours // 24 - 1}


def sample_general(weeks: list[Week], matrix: dict[str, dict[str, float]], n: int,
                   rng: random.Random) -> list[Week]:
    by_label = {label: [week for week in weeks if week.label == label] for label in LABELS}
    used: set[int] = set()
    sequence: list[Week] = []
    for _ in range(n):
        if sequence:
            weights = [matrix[sequence[-1].label][label] for label in LABELS]
        else:
            weights = [len(by_label[label]) for label in LABELS]
        label = rng.choices(LABELS, weights=weights)[0]
        options = [week for week in by_label[label] if week.start not in used]
        if not options:
            raise GenerationError(f"ran out of unused {label} weeks")
        week = rng.choice(options)
        used.add(week.start)
        sequence.append(week)
    return sequence


def stitched_return(series: Series, universe: list[str], starts: list[int], span: int) -> float:
    """Equal-weight buy-and-hold return of the chained weeks (prices are chained by ratio)."""
    total = 0.0
    for asset in universe:
        gross = 1.0
        for start in starts:
            gross *= 1 + period_return(series[asset], start, span)
        total += gross
    return total / len(universe) - 1


def _regime_candidate(kind: str, weeks: list[Week], matrix: dict[str, dict[str, float]],
                      params: Params, rng: random.Random) -> list[Week]:
    warmup = sample_general(weeks, matrix, params.warmup_weeks, rng)
    used = {week.start for week in warmup}
    label = REGIME_LABEL[kind]
    pool = [week for week in weeks if week.label == label and week.start not in used]
    if len(pool) < params.eval_weeks:
        raise GenerationError(f"{kind}: only {len(pool)} unused {label} weeks")
    return warmup + rng.sample(pool, params.eval_weeks)


def _evaluation_return(sequence: list[Week], series: Series, universe: list[str], params: Params) -> float:
    starts = [week.start for week in sequence[params.warmup_weeks:]]
    return stitched_return(series, universe, starts, params.span)


def sample_regime(kind: str, weeks: list[Week], matrix: dict[str, dict[str, float]], series: Series,
                  universe: list[str], params: Params, rng: random.Random) -> list[Week]:
    low, high = params.targets[kind]
    for _ in range(params.candidates):
        sequence = _regime_candidate(kind, weeks, matrix, params, rng)
        if low <= _evaluation_return(sequence, series, universe, params) <= high:
            return sequence
    raise GenerationError(f"{kind}: no candidate within {low:+.0%}..{high:+.0%} after {params.candidates} tries")


def regime_hit_rate(kind: str, weeks: list[Week], matrix: dict[str, dict[str, float]], series: Series,
                    universe: list[str], params: Params, rng: random.Random) -> float:
    low, high = params.targets[kind]
    hits = 0
    for _ in range(params.candidates):
        sequence = _regime_candidate(kind, weeks, matrix, params, rng)
        hits += low <= _evaluation_return(sequence, series, universe, params) <= high
    return hits / params.candidates


def sample_sequence(kind: str, weeks: list[Week], matrix: dict[str, dict[str, float]], series: Series,
                    universe: list[str], params: Params, rng: random.Random) -> list[Week]:
    if kind == GENERAL:
        return sample_general(weeks, matrix, params.warmup_weeks + params.eval_weeks, rng)
    return sample_regime(kind, weeks, matrix, series, universe, params, rng)
