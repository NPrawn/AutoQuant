import base64
import hashlib
import hmac
import json

import pytest

from scripts.contest_sim.jwt_token import issue
from scripts.contest_sim.run_contest import load_strategies
from scripts.contest_sim.scoring import percentiles, score

SECRET = "local-contest-secret-at-least-32-bytes"
CAPS = {"1H": 839, "4H": 209, "1D": 34}


def _decode(part: str) -> dict:
    return json.loads(base64.urlsafe_b64decode(part + "=" * (-len(part) % 4)))


def test_issue_signs_an_hs256_token_with_backend_claims():
    token = issue("00000000-0000-0000-0000-00000000c0de", SECRET, now=1_000)

    header, payload, signature = token.split(".")
    expected = hmac.new(SECRET.encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
    assert base64.urlsafe_b64decode(signature + "=" * (-len(signature) % 4)) == expected
    assert _decode(header)["alg"] == "HS256"
    assert _decode(payload) == {"iss": "a107", "sub": "00000000-0000-0000-0000-00000000c0de",
                                "iat": 1_000, "exp": 1_000 + 12 * 3600}


def test_issue_rejects_a_short_secret():
    with pytest.raises(ValueError):
        issue("member", "short")


def test_percentiles_give_ties_their_average_rank():
    assert percentiles({"a": 0.2, "b": 0.1, "c": 0.1}, higher_is_better=True) == {"a": 100.0, "b": 25.0, "c": 25.0}
    assert percentiles({"x": 0.1, "y": 0.3}, higher_is_better=False) == {"x": 100.0, "y": 0.0}


def _runs(general_return: float, mdds: tuple[float, float, float], exposure: float = 0.8) -> dict:
    runs = {"GENERAL": {"status": "COMPLETED", "return": general_return, "mdd": 0.1, "exposure": exposure}}
    for kind, mdd in zip(("BULL", "BEAR", "SIDEWAYS"), mdds):
        runs[kind] = {"status": "COMPLETED", "return": 0.0, "mdd": mdd, "exposure": exposure}
    return runs


def test_score_ranks_qualified_strategies_and_lists_disqualified_ones():
    results = {
        "s1": _runs(0.3, (0.10, 0.20, 0.05)),
        "s2": _runs(0.1, (0.05, 0.10, 0.02)),
        "s3": _runs(0.2, (0.20, 0.30, 0.10)),
        "cash": _runs(0.0, (0.0, 0.0, 0.0), exposure=0.0),
        "crash": _runs(0.1, (0.1, 0.6, 0.1)),
    }

    rows = score(results)

    assert [(row["strategy"], row["total"]) for row in rows[:3]] == [("s1", 75.0), ("s2", 50.0), ("s3", 25.0)]
    reasons = {row["strategy"]: row["disqualified"] for row in rows[3:]}
    assert "투자 비중" in reasons["cash"]
    assert "BEAR 최대 낙폭" in reasons["crash"]


def test_sample_strategies_are_valid_and_fit_the_default_warmup():
    strategies = load_strategies("scripts/contest_sim/strategies.json", CAPS)

    assert len(strategies) == 10


def test_load_strategies_rejects_a_period_longer_than_the_warmup(tmp_path):
    path = tmp_path / "too_long.json"
    path.write_text(json.dumps([{"name": "sma200", "dsl": {
        "timeframe": "1D",
        "filters": {"type": "CONDITION", "left": {"type": "FACTOR", "factor": "SMA_DISPARITY", "period": 200},
                    "operator": "GT", "right": {"type": "VALUE", "value": 0}},
        "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
        "selection": {"topN": 5},
        "rebalance": {"period": "WEEKLY"},
    }}]), encoding="utf-8")

    with pytest.raises(ValueError, match="exceeds 34"):
        load_strategies(str(path), CAPS)
