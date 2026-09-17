package com.ssafy.a107.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/*
 * 기준값은 2026-09-15 로컬 대회 재실행 결과다 (ADR-0012 결과, ai/docs/contest-validation/local-contest-2026-09-15-regime-a.json).
 * 같은 입력에서 ai/scripts/contest_sim/scoring.py 와 같은 점수가 나와야 한다.
 */
class ContestScoreCalculatorTest {

    private static final BigDecimal FULL_EXPOSURE = new BigDecimal("0.989");
    /* 그 회차 일반장의 균등 보유 수익률. 수익률 점수를 재는 기준이다. */
    private static final BigDecimal MARKET_RETURN = new BigDecimal("1.549");
    /* 데이터셋별 시장 낙폭. 국면 점수를 재는 기준이다. 값은 1회차 4시간봉 기준을 반올림했다. */
    private static final Map<MarketDatasetKind, BigDecimal> MARKET_DRAWDOWNS = Map.of(
            MarketDatasetKind.GENERAL, new BigDecimal("0.384"),
            MarketDatasetKind.BULL, new BigDecimal("0.357"),
            MarketDatasetKind.BEAR, new BigDecimal("0.504"),
            MarketDatasetKind.SIDEWAYS, new BigDecimal("0.390")
    );

    @DisplayName("점수는 시장 대비 수익률과 낙폭으로만 정해지고 순위는 총점 순이다. 실격은 투자 비중 미달뿐이다")
    @Test
    void scoreLikeTheLocalContestRun() {
        Map<String, UUID> ids = new LinkedHashMap<>();
        Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes = new LinkedHashMap<>();

        put(ids, outcomes, "equal-weight-hold", 1.721, 0.189, 0.121, 0.406, -0.109, 0.415, 0.062, 0.409, FULL_EXPOSURE);
        put(ids, outcomes, "momentum-7d", 1.837, 0.224, 0.239, 0.427, -0.049, 0.405, 0.040, 0.453, FULL_EXPOSURE);
        put(ids, outcomes, "low-volatility", 1.626, 0.253, 0.170, 0.457, -0.122, 0.458, -0.019, 0.430, FULL_EXPOSURE);
        put(ids, outcomes, "volume-surge", 1.170, 0.263, 0.059, 0.485, -0.168, 0.436, -0.226, 0.444, FULL_EXPOSURE);
        put(ids, outcomes, "momentum-24h", 1.316, 0.342, -0.272, 0.613, -0.098, 0.480, -0.236, 0.510, FULL_EXPOSURE);
        put(ids, outcomes, "drawdown-dip", 1.640, 0.275, 0.129, 0.588, -0.161, 0.508, -0.172, 0.435, FULL_EXPOSURE);
        put(ids, outcomes, "trend-filter", 1.394, 0.367, 0.084, 0.578, -0.407, 0.504, -0.401, 0.539, FULL_EXPOSURE);
        put(ids, outcomes, "all-in-one", 0.887, 0.539, 1.117, 0.548, 0.252, 0.461, -0.099, 0.472, FULL_EXPOSURE);
        put(ids, outcomes, "rsi-reversion", 0.038, 0.078, 0.202, 0.130, -0.067, 0.186, 0.160, 0.129,
                new BigDecimal("0.022"));
        put(ids, outcomes, "cash-only", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, BigDecimal.ZERO);

        List<ContestResult> results = ContestScoreCalculator.score(outcomes, MARKET_RETURN);

        Map<UUID, String> names = ids.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        List<ContestResult> ranked = results.stream().filter(result -> !result.isDisqualified()).toList();
        assertThat(ranked).extracting(result -> names.get(result.getEntryId())).containsExactly(
                "momentum-7d", "equal-weight-hold", "low-volatility", "drawdown-dip",
                "trend-filter", "volume-surge", "momentum-24h", "all-in-one");
        assertThat(ranked).extracting(ContestResult::getRanking).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(ranked).extracting(result -> result.getTotalScore().toPlainString()).containsExactly(
                "71.100", "63.050", "51.517", "45.217", "23.550", "18.833", "16.183", "14.800");
        /* 시장 +154.9% 대비 +28.8% 면 98점, 시장보다 37.9% 못 벌면 0점이다. */
        assertThat(ranked).extracting(result -> result.getReturnScore().toPlainString()).containsExactly(
                "98.000", "78.667", "62.833", "65.167", "24.167", "0.000", "11.167", "0.000");
        /* 시장만큼 빠지면 50점이다. 상승장에서 시장(35.7%)보다 7%p 더 빠진 1위는 38.3점이다. */
        assertThat(ranked).extracting(result -> result.getStabilityScore().toPlainString()).containsExactly(
                "44.200", "47.433", "40.200", "25.267", "22.933", "37.667", "21.200", "29.600");
        assertThat(ranked.getFirst().getBullRegimeScore().toPlainString()).isEqualTo("38.333");
        assertThat(ranked.getFirst().getBearRegimeScore().toPlainString()).isEqualTo("66.500");
        assertThat(ranked.getFirst().getSidewaysRegimeScore().toPlainString()).isEqualTo("39.500");

        Map<String, ContestResult> disqualified = results.stream()
                .filter(ContestResult::isDisqualified)
                .collect(Collectors.toMap(result -> names.get(result.getEntryId()), result -> result));
        assertThat(disqualified).containsOnlyKeys("rsi-reversion", "cash-only");
        assertThat(disqualified.get("rsi-reversion").getDisqualificationReason())
                .isEqualTo(ContestDisqualificationReason.LOW_EXPOSURE);
        assertThat(disqualified.get("cash-only").getDisqualificationReason())
                .isEqualTo(ContestDisqualificationReason.LOW_EXPOSURE);
    }

    @DisplayName("크게 잃고 깊이 빠져도 실격이 아니라 그 점수가 0점이다")
    @Test
    void scoreBlowupsInsteadOfDisqualifying() {
        UUID entryId = UUID.randomUUID();
        Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes = new LinkedHashMap<>();
        outcomes.put(MarketDatasetKind.GENERAL, completed(-0.4, 0.6));
        outcomes.put(MarketDatasetKind.BULL, completed(-0.4, 0.6));
        outcomes.put(MarketDatasetKind.BEAR, completed(0.0, 0.2));
        outcomes.put(MarketDatasetKind.SIDEWAYS, completed(0.0, 0.2));

        /* 시장 +10%·낙폭 20% 에서 −40%·낙폭 60% 면 수익률 점수와 상승장 국면 점수가 바닥이다. */
        List<ContestResult> results = ContestScoreCalculator.score(
                Map.of(entryId, outcomes), new BigDecimal("0.1"));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.isDisqualified()).isFalse();
            assertThat(result.getRanking()).isEqualTo(1);
            assertThat(result.getReturnScore().toPlainString()).isEqualTo("0.000");
            assertThat(result.getBullRegimeScore().toPlainString()).isEqualTo("0.000");
            assertThat(result.getBearRegimeScore().toPlainString()).isEqualTo("50.000");
        });
    }

    @DisplayName("실패한 백테스트가 있으면 그 데이터셋으로 실격한다")
    @Test
    void disqualifyFailedBacktest() {
        UUID entryId = UUID.randomUUID();
        Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes = new LinkedHashMap<>();
        outcomes.put(MarketDatasetKind.GENERAL, completed(0.1, 0.2));
        outcomes.put(MarketDatasetKind.BULL, completed(0.1, 0.2));
        outcomes.put(MarketDatasetKind.BEAR, ContestEvaluationOutcome.failed());
        outcomes.put(MarketDatasetKind.SIDEWAYS, completed(0.0, 0.1));

        List<ContestResult> results = ContestScoreCalculator.score(Map.of(entryId, outcomes), MARKET_RETURN);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getDisqualificationReason()).isEqualTo(ContestDisqualificationReason.BACKTEST_FAILED);
            assertThat(result.getDisqualifiedKind()).isEqualTo(MarketDatasetKind.BEAR);
        });
    }

    @DisplayName("점수는 남들과 무관하다. 시장과 똑같이 벌고 똑같이 빠지면 모두 50점이다")
    @Test
    void scoreTheOnlyEntryOnItsOwnNumbers() {
        UUID entryId = UUID.randomUUID();
        Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes = new LinkedHashMap<>();
        for (MarketDatasetKind kind : MarketDatasetKind.values()) {
            outcomes.put(kind, completed(0.1, 0.2));
        }

        /* 시장 +10%·낙폭 20% 인 회차에서 +10%·낙폭 20% 를 내면 수익률도 국면도 시장과 같다. */
        List<ContestResult> results = ContestScoreCalculator.score(
                Map.of(entryId, outcomes), new BigDecimal("0.1"));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getReturnScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getBullRegimeScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getStabilityScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getTotalScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getRanking()).isEqualTo(1);
        });
    }

    @DisplayName("국면 점수는 시장보다 30%p 덜 빠지면 100점, 30%p 더 빠지면 0점이다")
    @Test
    void measureRegimeScoreAgainstTheMarketDrawdown() {
        BigDecimal market = new BigDecimal("0.40");

        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.40"), market)).isEqualTo(50.0);
        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.10"), market)).isCloseTo(100.0, within(1e-9));
        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.05"), market)).isEqualTo(100.0);
        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.49"), market)).isCloseTo(35.0, within(1e-9));
        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.50"), new BigDecimal("0.10"))).isEqualTo(0.0);
    }

    /* 시장 낙폭은 20% 로 둔다. */
    private static ContestEvaluationOutcome completed(double cumulativeReturn, double maximumDrawdown) {
        return ContestEvaluationOutcome.completed(
                BigDecimal.valueOf(cumulativeReturn),
                BigDecimal.valueOf(maximumDrawdown),
                FULL_EXPOSURE,
                new BigDecimal("0.2")
        );
    }

    private static void put(
            Map<String, UUID> ids,
            Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes,
            String name,
            double generalReturn, double generalMdd,
            double bullReturn, double bullMdd,
            double bearReturn, double bearMdd,
            double sidewaysReturn, double sidewaysMdd,
            BigDecimal exposure
    ) {
        UUID entryId = UUID.randomUUID();
        ids.put(name, entryId);

        Map<MarketDatasetKind, ContestEvaluationOutcome> byKind = new LinkedHashMap<>();
        byKind.put(MarketDatasetKind.GENERAL, outcome(MarketDatasetKind.GENERAL, generalReturn, generalMdd, exposure));
        byKind.put(MarketDatasetKind.BULL, outcome(MarketDatasetKind.BULL, bullReturn, bullMdd, exposure));
        byKind.put(MarketDatasetKind.BEAR, outcome(MarketDatasetKind.BEAR, bearReturn, bearMdd, exposure));
        byKind.put(MarketDatasetKind.SIDEWAYS, outcome(MarketDatasetKind.SIDEWAYS, sidewaysReturn, sidewaysMdd, exposure));
        outcomes.put(entryId, byKind);
    }

    private static ContestEvaluationOutcome outcome(
            MarketDatasetKind kind,
            double cumulativeReturn,
            double maximumDrawdown,
            BigDecimal exposure
    ) {
        return ContestEvaluationOutcome.completed(
                BigDecimal.valueOf(cumulativeReturn),
                BigDecimal.valueOf(maximumDrawdown),
                exposure,
                MARKET_DRAWDOWNS.get(kind)
        );
    }
}
