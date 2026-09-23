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
    /* 데이터셋별 시장 낙폭. 국면 점수를 재는 기준이다. 값은 1회차 4시간봉 기준을 반올림했다. */
    private static final Map<MarketDatasetKind, BigDecimal> MARKET_DRAWDOWNS = Map.of(
            MarketDatasetKind.GENERAL, new BigDecimal("0.384"),
            MarketDatasetKind.BULL, new BigDecimal("0.357"),
            MarketDatasetKind.BEAR, new BigDecimal("0.504"),
            MarketDatasetKind.SIDEWAYS, new BigDecimal("0.390")
    );

    @DisplayName("점수는 원금 기준 수익률과 투자 비중으로 정규화한 낙폭으로 정해진다. 비중 미달은 국면 중립이다")
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

        List<ContestResult> results = ContestScoreCalculator.score(outcomes);

        Map<UUID, String> names = ids.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
        List<ContestResult> ranked = results.stream().filter(result -> !result.isDisqualified()).toList();
        assertThat(ranked).extracting(result -> names.get(result.getEntryId())).containsExactly(
                "momentum-7d", "equal-weight-hold", "low-volatility", "drawdown-dip",
                "volume-surge", "trend-filter", "momentum-24h", "all-in-one",
                "rsi-reversion", "cash-only");
        assertThat(ranked).extracting(ContestResult::getRanking).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(ranked).extracting(result -> result.getTotalScore().toPlainString()).containsExactly(
                "69.382", "68.844", "64.356", "58.467", "54.525", "53.132", "50.754", "44.875",
                "26.697", "25.000");
        /* 자산 3배가 100점이다. +183.7% 로 2.84 배가 된 1위는 94.9점이다. */
        assertThat(ranked).extracting(result -> result.getReturnScore().toPlainString()).containsExactly(
                "94.915", "91.115", "87.880", "88.364", "70.519", "79.461", "76.446", "57.799",
                "3.395", "0.000");
        /* 같은 비중으로 시장을 든 경우만큼 빠지면 50점이다. 비중 미달 둘은 국면이 중립이라 50점이다. */
        assertThat(ranked).extracting(result -> result.getStabilityScore().toPlainString()).containsExactly(
                "43.849", "46.573", "40.833", "28.569", "38.532", "26.803", "25.062", "31.952",
                "50.000", "50.000");
        assertThat(ranked.getFirst().getBullRegimeScore().toPlainString()).isEqualTo("39.531");
        assertThat(ranked.getFirst().getBearRegimeScore().toPlainString()).isEqualTo("59.375");
        assertThat(ranked.getFirst().getSidewaysRegimeScore().toPlainString()).isEqualTo("41.277");

        /* 비중 미달은 실격이 아니다. 국면이 중립 50점이라 안정성만으로도 실제 투자한 제출을 못 이긴다. */
        assertThat(results).noneMatch(ContestResult::isDisqualified);
    }

    @DisplayName("크게 잃고 깊이 빠져도 실격이 아니라 그 점수가 내려간다")
    @Test
    void scoreBlowupsInsteadOfDisqualifying() {
        UUID entryId = UUID.randomUUID();
        Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes = new LinkedHashMap<>();
        outcomes.put(MarketDatasetKind.GENERAL, completed(-0.4, 0.6));
        outcomes.put(MarketDatasetKind.BULL, completed(-0.4, 0.6));
        outcomes.put(MarketDatasetKind.BEAR, completed(0.0, 0.2));
        outcomes.put(MarketDatasetKind.SIDEWAYS, completed(0.0, 0.2));

        /* −40% 면 수익률 점수가 음수고, 시장 낙폭 20% 의 세 배로 빠진 상승장은 국면 점수가 바닥이다. */
        List<ContestResult> results = ContestScoreCalculator.score(Map.of(entryId, outcomes));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.isDisqualified()).isFalse();
            assertThat(result.getRanking()).isEqualTo(1);
            assertThat(result.getReturnScore().toPlainString()).isEqualTo("-46.497");
            assertThat(result.getBullRegimeScore().toPlainString()).isEqualTo("0.000");
            assertThat(result.getBearRegimeScore().toPlainString()).isEqualTo("49.444");
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

        List<ContestResult> results = ContestScoreCalculator.score(Map.of(entryId, outcomes));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getDisqualificationReason()).isEqualTo(ContestDisqualificationReason.BACKTEST_FAILED);
            assertThat(result.getDisqualifiedKind()).isEqualTo(MarketDatasetKind.BEAR);
        });
    }

    @DisplayName("점수는 남들과 무관하다. 시장을 그대로 들면 국면 점수가 50점이다")
    @Test
    void scoreTheOnlyEntryOnItsOwnNumbers() {
        UUID entryId = UUID.randomUUID();
        Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes = new LinkedHashMap<>();
        for (MarketDatasetKind kind : MarketDatasetKind.values()) {
            outcomes.put(kind, completed(0.1, 0.2, BigDecimal.ONE));
        }

        /* 시장 낙폭 20% 를 비중 100% 로 그대로 맞은 제출이다. 수익률은 원금 기준이라 +10% 가 8.676점이다. */
        List<ContestResult> results = ContestScoreCalculator.score(Map.of(entryId, outcomes));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.getReturnScore().toPlainString()).isEqualTo("8.676");
            assertThat(result.getBullRegimeScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getStabilityScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getTotalScore().toPlainString()).isEqualTo("29.338");
            assertThat(result.getRanking()).isEqualTo(1);
        });
    }

    @DisplayName("수익률 점수는 본전이 0점, 자산 3배가 100점이고 −200점에서 자른다")
    @Test
    void measureReturnScoreAgainstTheStartingCapital() {
        assertThat(ContestScoreCalculator.returnScore(BigDecimal.ZERO)).isEqualTo(0.0);
        assertThat(ContestScoreCalculator.returnScore(new BigDecimal("2"))).isCloseTo(100.0, within(1e-9));
        assertThat(ContestScoreCalculator.returnScore(new BigDecimal("8"))).isCloseTo(200.0, within(1e-9));
        assertThat(ContestScoreCalculator.returnScore(new BigDecimal("-0.666666666666667")))
                .isCloseTo(-100.0, within(1e-9));
        /* 자산이 9분의 1 아래면 하한에 걸린다. 전액 손실도 같은 값이다. */
        assertThat(ContestScoreCalculator.returnScore(new BigDecimal("-0.9"))).isEqualTo(-200.0);
        assertThat(ContestScoreCalculator.returnScore(new BigDecimal("-1"))).isEqualTo(-200.0);
    }

    @DisplayName("일반장 투자 비중이 하한 미만이면 실격 대신 세 국면이 중립 50점이다")
    @Test
    void neutraliseRegimeScoresWhenTheEntryBarelyBuys() {
        UUID entryId = UUID.randomUUID();
        Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes = new LinkedHashMap<>();
        for (MarketDatasetKind kind : MarketDatasetKind.values()) {
            /* 비중 2% 에 낙폭 0.1%. 정규화만 하면 국면 점수가 93.75 점이라 실제로 투자한 제출을 이긴다. */
            outcomes.put(kind, completed(0.0, 0.001, new BigDecimal("0.02")));
        }

        List<ContestResult> results = ContestScoreCalculator.score(Map.of(entryId, outcomes));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.isDisqualified()).isFalse();
            assertThat(result.getBullRegimeScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getBearRegimeScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getSidewaysRegimeScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getStabilityScore().toPlainString()).isEqualTo("50.000");
            assertThat(result.getTotalScore().toPlainString()).isEqualTo("25.000");
        });
    }

    @DisplayName("국면 점수는 같은 비중으로 시장을 든 경우와 견준다. 덜 사면 낙폭도 같이 나눈다")
    @Test
    void measureRegimeScoreAgainstTheExposureAdjustedMarketDrawdown() {
        BigDecimal market = new BigDecimal("0.40");
        BigDecimal full = BigDecimal.ONE;

        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.40"), full, market)).isEqualTo(50.0);
        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.20"), full, market)).isEqualTo(75.0);
        assertThat(ContestScoreCalculator.regimeScore(BigDecimal.ZERO, full, market)).isEqualTo(100.0);
        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.80"), full, market)).isEqualTo(0.0);

        /* 절반만 산 제출이 절반만 빠졌으면 시장을 그대로 든 것과 같다. 정규화가 없으면 75점이 된다. */
        assertThat(ContestScoreCalculator.regimeScore(new BigDecimal("0.20"), new BigDecimal("0.5"), market))
                .isEqualTo(50.0);
        /* 아무것도 사지 않은 장세는 식의 극한인 50점이다. */
        assertThat(ContestScoreCalculator.regimeScore(BigDecimal.ZERO, BigDecimal.ZERO, market)).isEqualTo(50.0);
    }

    /* 시장 낙폭은 20% 로 둔다. */
    private static ContestEvaluationOutcome completed(double cumulativeReturn, double maximumDrawdown) {
        return completed(cumulativeReturn, maximumDrawdown, FULL_EXPOSURE);
    }

    private static ContestEvaluationOutcome completed(
            double cumulativeReturn, double maximumDrawdown, BigDecimal exposure) {
        return ContestEvaluationOutcome.completed(
                BigDecimal.valueOf(cumulativeReturn),
                BigDecimal.valueOf(maximumDrawdown),
                exposure,
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
