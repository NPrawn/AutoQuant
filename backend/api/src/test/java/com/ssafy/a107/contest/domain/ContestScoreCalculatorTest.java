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

/*
 * 기준값은 2026-09-15 로컬 대회 재실행 결과다 (ADR-0012 결과, ai/docs/contest-validation/local-contest-2026-09-15-regime-a.json).
 * 같은 입력에서 ai/scripts/contest_sim/scoring.py 와 같은 점수가 나와야 한다.
 */
class ContestScoreCalculatorTest {

    private static final BigDecimal FULL_EXPOSURE = new BigDecimal("0.989");
    /* 그 회차 일반장의 균등 보유 수익률. 수익률 점수를 재는 기준이다. */
    private static final BigDecimal MARKET_RETURN = new BigDecimal("1.549");

    @DisplayName("점수는 시장 대비 수익률과 낙폭으로만 정해지고 순위는 총점 순이다")
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
        assertThat(ranked).extracting(result -> names.get(result.getEntryId()))
                .containsExactly("momentum-7d", "equal-weight-hold", "low-volatility", "volume-surge");
        assertThat(ranked).extracting(ContestResult::getRanking).containsExactly(1, 2, 3, 4);
        assertThat(ranked).extracting(result -> result.getTotalScore().toPlainString())
                .containsExactly("55.180", "48.133", "36.197", "3.300");
        /* 시장 +154.9% 대비 +28.8% 면 98점, 시장보다 37.9% 못 벌면 0점이다. */
        assertThat(ranked).extracting(result -> result.getReturnScore().toPlainString())
                .containsExactly("98.000", "78.667", "62.833", "0.000");
        /* 낙폭이 실격선 50% 에 가까워 국면 점수가 낮다. */
        assertThat(ranked).extracting(result -> result.getStabilityScore().toPlainString())
                .containsExactly("12.360", "17.600", "9.560", "6.600");
        assertThat(ranked.getFirst().getBullRegimeScore().toPlainString()).isEqualTo("14.600");
        assertThat(ranked.getFirst().getSidewaysRegimeScore().toPlainString()).isEqualTo("9.400");

        Map<String, ContestResult> disqualified = results.stream()
                .filter(ContestResult::isDisqualified)
                .collect(Collectors.toMap(result -> names.get(result.getEntryId()), result -> result));
        assertThat(disqualified).hasSize(6);
        assertThat(disqualified.get("momentum-24h").getDisqualificationReason())
                .isEqualTo(ContestDisqualificationReason.MDD_ABOVE_LIMIT);
        assertThat(disqualified.get("momentum-24h").getDisqualifiedKind()).isEqualTo(MarketDatasetKind.BULL);
        assertThat(disqualified.get("all-in-one").getDisqualifiedKind()).isEqualTo(MarketDatasetKind.GENERAL);
        assertThat(disqualified.get("rsi-reversion").getDisqualificationReason())
                .isEqualTo(ContestDisqualificationReason.LOW_EXPOSURE);
        assertThat(disqualified.get("cash-only").getDisqualificationReason())
                .isEqualTo(ContestDisqualificationReason.LOW_EXPOSURE);
    }

    @DisplayName("수익률이 실격선 아래면 낙폭보다 먼저 걸린다")
    @Test
    void preferReturnLimitOverDrawdownLimit() {
        UUID entryId = UUID.randomUUID();
        Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes = new LinkedHashMap<>();
        outcomes.put(MarketDatasetKind.GENERAL, completed(0.1, 0.2));
        outcomes.put(MarketDatasetKind.BULL, completed(-0.35, 0.6));
        outcomes.put(MarketDatasetKind.BEAR, completed(0.0, 0.1));
        outcomes.put(MarketDatasetKind.SIDEWAYS, completed(0.0, 0.1));

        List<ContestResult> results = ContestScoreCalculator.score(Map.of(entryId, outcomes), MARKET_RETURN);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getDisqualificationReason())
                    .isEqualTo(ContestDisqualificationReason.RETURN_BELOW_LIMIT);
            assertThat(result.getDisqualifiedKind()).isEqualTo(MarketDatasetKind.BULL);
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

    @DisplayName("점수는 남들과 무관하다. 제출이 하나여도 성적 그대로 나온다")
    @Test
    void scoreTheOnlyEntryOnItsOwnNumbers() {
        UUID entryId = UUID.randomUUID();
        Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes = new LinkedHashMap<>();
        for (MarketDatasetKind kind : MarketDatasetKind.values()) {
            outcomes.put(kind, completed(0.1, 0.2));
        }

        /* 시장 +10% 인 회차에서 +10% 를 내면 시장과 같아 50점, 낙폭 20% 면 국면 점수 60점이다. */
        List<ContestResult> results = ContestScoreCalculator.score(
                Map.of(entryId, outcomes), new BigDecimal("0.1"));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getReturnScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getStabilityScore().toPlainString()).isEqualTo("60.000");
            assertThat(result.getTotalScore().toPlainString()).isEqualTo("55.000");
            assertThat(result.getRanking()).isEqualTo(1);
        });
    }

    private static ContestEvaluationOutcome completed(double cumulativeReturn, double maximumDrawdown) {
        return ContestEvaluationOutcome.completed(
                BigDecimal.valueOf(cumulativeReturn),
                BigDecimal.valueOf(maximumDrawdown),
                FULL_EXPOSURE
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
        byKind.put(MarketDatasetKind.GENERAL, ContestEvaluationOutcome.completed(
                BigDecimal.valueOf(generalReturn), BigDecimal.valueOf(generalMdd), exposure));
        byKind.put(MarketDatasetKind.BULL, ContestEvaluationOutcome.completed(
                BigDecimal.valueOf(bullReturn), BigDecimal.valueOf(bullMdd), exposure));
        byKind.put(MarketDatasetKind.BEAR, ContestEvaluationOutcome.completed(
                BigDecimal.valueOf(bearReturn), BigDecimal.valueOf(bearMdd), exposure));
        byKind.put(MarketDatasetKind.SIDEWAYS, ContestEvaluationOutcome.completed(
                BigDecimal.valueOf(sidewaysReturn), BigDecimal.valueOf(sidewaysMdd), exposure));
        outcomes.put(entryId, byKind);
    }
}
