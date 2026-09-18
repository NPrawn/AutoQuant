import base64
import hashlib
import hmac
import json

import pytest

from scripts.contest_sim.jwt_token import issue
from scripts.contest_sim.run_contest import load_strategies
from scripts.contest_sim.scoring import market_drawdowns, percentiles, regime_score, score

SECRET = "local-contest-secret-at-least-32-bytes"
CAPS = {"1H": 5039, "4H": 1259, "1D": 209}


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
    runs = {"GENERAL": {"status": "COMPLETED", "return": general_return, "mdd": 0.1, "exposure": exposure,
                        "market_mdd": 0.2}}
    for kind, mdd in zip(("BULL", "BEAR", "SIDEWAYS"), mdds):
        runs[kind] = {"status": "COMPLETED", "return": 0.0, "mdd": mdd, "exposure": exposure, "market_mdd": 0.2}
    return runs


def test_score_ranks_qualified_strategies_and_lists_disqualified_ones():
    results = {
        "s1": _runs(0.3, (0.10, 0.20, 0.05)),
        "s2": _runs(0.1, (0.05, 0.10, 0.02)),
        "s3": _runs(0.2, (0.20, 0.30, 0.10)),
        "cash": _runs(0.0, (0.0, 0.0, 0.0), exposure=0.0),
        "crash": _runs(0.1, (0.1, 0.6, 0.1)),
    }

    # 시장 +10%, 시장 낙폭 20% 기준. 점수는 남들과 무관하게 시장 대비 수익률과 낙폭으로만 정해진다.
    rows = score(results, market_return=0.1)

    ranked = [(row["strategy"], round(row["total"], 3)) for row in rows[:4]]
    # crash 는 하락장 낙폭 60% 로 실격이 아니라 그 국면 점수가 0점이 된다.
    assert ranked == [("s1", 70.833), ("s2", 60.5), ("s3", 55.0), ("crash", 38.333)]
    reasons = {row["strategy"]: row["disqualified"] for row in rows[4:]}
    assert reasons == {"cash": "평균 투자 비중 0.0% < 30%"}


def test_regime_score_is_fifty_when_the_strategy_falls_as_far_as_the_market():
    assert regime_score(0.36, 0.36) == 50.0
    assert round(regime_score(0.06, 0.36), 6) == 100.0
    assert regime_score(0.70, 0.36) == 0.0


def _candle(asset: str, minutes: int, open_time: str, close: float) -> str:
    return f"{asset},{minutes},{open_time},{close},{close},{close},{close},1,1,{open_time}\n"


def test_market_drawdowns_hold_the_basket_from_the_last_close_before_the_period(tmp_path):
    path = tmp_path / "BEAR.csv"
    rows = ["asset_id,interval_minutes,open_time,open_price,high_price,low_price,close_price,trade_volume,"
            "trade_amount,last_event_time\n"]
    # 기준가: 평가 시작(01-02 00:00) 전에 닫힌 마지막 봉. 앞선 봉 200 은 기준이 아니다.
    for asset in ("a", "b"):
        rows.append(_candle(asset, 60, "2030-01-01T22:00:00Z", 200))
        rows.append(_candle(asset, 60, "2030-01-01T23:00:00Z", 100))
        rows.append(_candle(asset, 240, "2030-01-01T20:00:00Z", 100))
        rows.append(_candle(asset, 1440, "2030-01-01T00:00:00Z", 100))
    # a 는 120 → 90 → 110, b 는 100 그대로. 바구니는 1.10 → 0.95 → 1.05 라 낙폭 1 − 0.95/1.10.
    for hour, close in enumerate((120, 90, 110)):
        rows.append(_candle("a", 60, f"2030-01-02T{hour:02d}:00:00Z", close))
    for hour in range(3):
        rows.append(_candle("b", 60, f"2030-01-02T{hour:02d}:00:00Z", 100))
    # 4시간봉은 올랐다 제자리라 고점 1.05 에서 1.00 으로, 평가 끝 뒤 봉은 보지 않는다.
    rows.append(_candle("a", 240, "2030-01-02T00:00:00Z", 110))
    rows.append(_candle("a", 240, "2030-01-02T04:00:00Z", 100))
    rows.append(_candle("a", 240, "2030-01-03T00:00:00Z", 10))
    path.write_text("".join(rows), encoding="utf-8")

    drawdowns = market_drawdowns(str(path), "2030-01-02T00:00:00Z", "2030-01-03T00:00:00Z")

    assert round(drawdowns["1H"], 6) == round(1 - 0.95 / 1.10, 6)
    assert round(drawdowns["4H"], 6) == round(1 - 1.00 / 1.05, 6)
    assert drawdowns["1D"] == 0.0


def test_sample_strategies_are_valid_and_fit_the_default_warmup():
    strategies = load_strategies("scripts/contest_sim/strategies.json", CAPS)

    assert len(strategies) == 10


def test_load_strategies_rejects_a_period_longer_than_the_warmup(tmp_path):
    path = tmp_path / "too_long.json"
    path.write_text(json.dumps([{"name": "sma210", "dsl": {
        "timeframe": "1D",
        "filters": {"type": "CONDITION", "left": {"type": "FACTOR", "factor": "SMA_DISPARITY", "period": 210},
                    "operator": "GT", "right": {"type": "VALUE", "value": 0}},
        "ranking": {"factor": "RETURN", "period": 20, "direction": "DESC"},
        "selection": {"topN": 5},
        "rebalance": {"period": "WEEKLY"},
    }}]), encoding="utf-8")

    with pytest.raises(ValueError, match="exceeds 209"):
        load_strategies(str(path), CAPS)
