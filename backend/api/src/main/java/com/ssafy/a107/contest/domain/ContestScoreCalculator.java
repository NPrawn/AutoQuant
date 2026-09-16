package com.ssafy.a107.contest.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * 제출별 점수와 순위를 낸다 (ADR-0002, ADR-0003, 대회 기능 설계 7절).
 *
 * - 실격: 데이터셋을 일반장·상승장·하락장·횡보장 순으로 보며 실패 → 수익률 → 낙폭 순으로 처음 걸린 사유 하나.
 *   네 벌을 모두 통과하면 일반장 평균 투자 비중을 본다.
 * - 백분위: 실격이 아닌 제출끼리 (n − 순위) / (n − 1) × 100. 동점은 평균 순위를 쓰고, n = 1 이면 100.
 * - 수익률 점수는 일반장 누적 수익률 백분위, 안정성 점수는 장세 3벌 낙폭 백분위의 평균 × 0.6 + 최저 × 0.4.
 *
 * 같은 규칙의 참고 구현이 ai/scripts/contest_sim/scoring.py 다.
 */
public final class ContestScoreCalculator {

    private static final List<MarketDatasetKind> REGIMES =
            List.of(MarketDatasetKind.BULL, MarketDatasetKind.BEAR, MarketDatasetKind.SIDEWAYS);
    private static final int SCORE_SCALE = 3;

    private ContestScoreCalculator() {
    }

    public static List<ContestResult> score(Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes) {
        Map<UUID, ContestResult> disqualifiedResults = new LinkedHashMap<>();
        List<UUID> qualified = new ArrayList<>();

        for (Map.Entry<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> entry : outcomes.entrySet()) {
            ContestResult disqualified = disqualify(entry.getKey(), entry.getValue());
            if (disqualified == null) {
                qualified.add(entry.getKey());
            } else {
                disqualifiedResults.put(entry.getKey(), disqualified);
            }
        }

        List<ContestResult> results = new ArrayList<>(score(qualified, outcomes));
        results.addAll(disqualifiedResults.values());
        return List.copyOf(results);
    }

    private static List<ContestResult> score(
            List<UUID> qualified,
            Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes
    ) {
        if (qualified.isEmpty()) {
            return List.of();
        }

        Map<UUID, Double> returnScores = percentiles(value(qualified, outcomes, MarketDatasetKind.GENERAL, true), true);
        Map<MarketDatasetKind, Map<UUID, Double>> regimeScores = new LinkedHashMap<>();
        for (MarketDatasetKind regime : REGIMES) {
            regimeScores.put(regime, percentiles(value(qualified, outcomes, regime, false), false));
        }

        Map<UUID, Double> totals = new LinkedHashMap<>();
        Map<UUID, Double> stabilities = new LinkedHashMap<>();
        for (UUID entryId : qualified) {
            List<Double> perRegime = REGIMES.stream()
                    .map(regime -> regimeScores.get(regime).get(entryId))
                    .toList();
            double mean = perRegime.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
            double worst = perRegime.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
            double stability = 0.6 * mean + 0.4 * worst;

            stabilities.put(entryId, stability);
            totals.put(entryId, 0.5 * returnScores.get(entryId) + 0.5 * stability);
        }

        Map<UUID, Integer> rankings = rankings(totals);
        List<ContestResult> results = new ArrayList<>();
        for (UUID entryId : qualified) {
            results.add(ContestResult.scored(
                    entryId,
                    scale(returnScores.get(entryId)),
                    scale(regimeScores.get(MarketDatasetKind.BULL).get(entryId)),
                    scale(regimeScores.get(MarketDatasetKind.BEAR).get(entryId)),
                    scale(regimeScores.get(MarketDatasetKind.SIDEWAYS).get(entryId)),
                    scale(stabilities.get(entryId)),
                    scale(totals.get(entryId)),
                    rankings.get(entryId)
            ));
        }
        results.sort(Comparator.comparing(ContestResult::getRanking));
        return results;
    }

    private static ContestResult disqualify(UUID entryId, Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes) {
        for (MarketDatasetKind kind : MarketDatasetKind.values()) {
            ContestEvaluationOutcome outcome = outcomes.get(kind);
            if (outcome == null || !outcome.completed()) {
                return ContestResult.disqualified(entryId, ContestDisqualificationReason.BACKTEST_FAILED, kind);
            }
            if (outcome.cumulativeReturn().compareTo(ContestScoringPolicy.MINIMUM_RETURN) < 0) {
                return ContestResult.disqualified(entryId, ContestDisqualificationReason.RETURN_BELOW_LIMIT, kind);
            }
            if (outcome.maximumDrawdown().compareTo(ContestScoringPolicy.MAXIMUM_DRAWDOWN) > 0) {
                return ContestResult.disqualified(entryId, ContestDisqualificationReason.MDD_ABOVE_LIMIT, kind);
            }
        }

        BigDecimal exposure = outcomes.get(MarketDatasetKind.GENERAL).averageExposure();
        if (exposure == null || exposure.compareTo(ContestScoringPolicy.MINIMUM_EXPOSURE) < 0) {
            return ContestResult.disqualified(
                    entryId,
                    ContestDisqualificationReason.LOW_EXPOSURE,
                    MarketDatasetKind.GENERAL
            );
        }
        return null;
    }

    private static Map<UUID, Double> value(
            List<UUID> qualified,
            Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes,
            MarketDatasetKind kind,
            boolean useReturn
    ) {
        Map<UUID, Double> values = new LinkedHashMap<>();
        for (UUID entryId : qualified) {
            ContestEvaluationOutcome outcome = outcomes.get(entryId).get(kind);
            values.put(entryId, useReturn
                    ? outcome.cumulativeReturn().doubleValue()
                    : outcome.maximumDrawdown().doubleValue());
        }
        return values;
    }

    /** 순위 1 이 가장 좋은 성적이고, 동점은 평균 순위를 쓴다. */
    private static Map<UUID, Double> percentiles(Map<UUID, Double> values, boolean higherIsBetter) {
        int n = values.size();
        if (n == 1) {
            return Map.of(values.keySet().iterator().next(), 100.0);
        }

        List<UUID> ordered = new ArrayList<>(values.keySet());
        Comparator<UUID> comparator = Comparator.comparingDouble(values::get);
        ordered.sort(higherIsBetter ? comparator.reversed() : comparator);

        Map<UUID, Double> percentiles = new LinkedHashMap<>();
        int index = 0;
        while (index < n) {
            int last = index;
            while (last + 1 < n && values.get(ordered.get(last + 1)).equals(values.get(ordered.get(index)))) {
                last++;
            }
            double rank = (index + last) / 2.0 + 1;
            for (UUID entryId : ordered.subList(index, last + 1)) {
                percentiles.put(entryId, (n - rank) / (n - 1) * 100);
            }
            index = last + 1;
        }
        return percentiles;
    }

    /** 총점이 같으면 같은 순위를 준다. */
    private static Map<UUID, Integer> rankings(Map<UUID, Double> totals) {
        List<UUID> ordered = new ArrayList<>(totals.keySet());
        ordered.sort(Comparator.comparingDouble(totals::get).reversed());

        Map<UUID, Integer> rankings = new LinkedHashMap<>();
        int index = 0;
        while (index < ordered.size()) {
            int last = index;
            while (last + 1 < ordered.size()
                    && totals.get(ordered.get(last + 1)).equals(totals.get(ordered.get(index)))) {
                last++;
            }
            for (UUID entryId : ordered.subList(index, last + 1)) {
                rankings.put(entryId, index + 1);
            }
            index = last + 1;
        }
        return rankings;
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
