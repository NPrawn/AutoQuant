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
 * 점수는 나 혼자로 계산되는 절대값이라 제출이 끝나는 대로 확정된다. 남들과 견주는 값은 순위뿐이고,
 * 순위는 그때까지 채점된 제출 사이의 자리라 마감 전에는 잠정이다.
 *
 * - 실격: 데이터셋을 일반장·상승장·하락장·횡보장 순으로 보며 처음 실패한 백테스트 하나. 네 벌이 모두 끝났으면
 *   일반장 평균 투자 비중을 본다. 크게 잃거나 깊이 빠진 전략은 실격이 아니라 아래 점수가 0점으로 내려간다.
 * - 수익률 점수: 일반장에서 시장보다 얼마나 더 벌었는지. 시장과 같으면 50점, 시장 대비 +30%면 100점,
 *   시장 대비 −30%면 0점이다. 장세 난이도는 시장 수익률이 흡수한다.
 * - 국면 점수: 구간 최대 낙폭을 그 장세의 시장 낙폭과 견준다. 시장과 같으면 50점, 시장보다 30%p 덜 빠지면
 *   100점, 30%p 더 빠지면 0점이다. 장세가 거칠어 누구나 깊이 빠지는 구간도 시장 낙폭이 흡수한다.
 * - 안정성 점수: 상승·하락·횡보 국면 점수의 평균 × 0.6 + 최저 × 0.4.
 * - 총점: 수익률 점수 × 0.5 + 안정성 점수 × 0.5.
 *
 * 같은 규칙의 참고 구현이 ai/scripts/contest_sim/scoring.py 다.
 */
public final class ContestScoreCalculator {

    private static final List<MarketDatasetKind> REGIMES =
            List.of(MarketDatasetKind.BULL, MarketDatasetKind.BEAR, MarketDatasetKind.SIDEWAYS);
    private static final int SCORE_SCALE = 3;
    /* 수익률 점수의 척도. 시장 대비 −30%가 0점, +30%가 100점이다. */
    private static final double RETURN_SPAN = 0.30;
    /* 국면 점수의 척도. 시장 낙폭보다 30%p 더 빠지면 0점, 30%p 덜 빠지면 100점이다. */
    private static final double DRAWDOWN_SPAN = 0.30;

    private ContestScoreCalculator() {
    }

    /*
     * generalMarketReturn 은 일반장 데이터셋의 균등 보유 수익률이다. 회차 난이도를 여기서 흡수한다.
     */
    public static List<ContestResult> score(
            Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes,
            BigDecimal generalMarketReturn
    ) {
        Map<UUID, ContestResult> disqualifiedResults = new LinkedHashMap<>();
        List<UUID> qualified = new ArrayList<>();

        for (Map.Entry<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> entry : outcomes.entrySet()) {
            Disqualification disqualification = disqualification(entry.getValue());
            if (disqualification == null) {
                qualified.add(entry.getKey());
            } else {
                disqualifiedResults.put(entry.getKey(), ContestResult.disqualified(
                        entry.getKey(), disqualification.reason(), disqualification.kind()));
            }
        }

        List<ContestResult> results = new ArrayList<>(score(qualified, outcomes, generalMarketReturn));
        results.addAll(disqualifiedResults.values());
        return List.copyOf(results);
    }

    /** 실격 사유와 걸린 데이터셋. 통과하면 null 이다. 마감 전 내 성적 화면도 이 판정을 쓴다. */
    public static Disqualification disqualification(Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes) {
        for (MarketDatasetKind kind : MarketDatasetKind.values()) {
            ContestEvaluationOutcome outcome = outcomes.get(kind);
            if (outcome == null || !outcome.completed()) {
                return new Disqualification(ContestDisqualificationReason.BACKTEST_FAILED, kind);
            }
        }

        BigDecimal exposure = outcomes.get(MarketDatasetKind.GENERAL).averageExposure();
        if (exposure == null || exposure.compareTo(ContestScoringPolicy.MINIMUM_EXPOSURE) < 0) {
            return new Disqualification(ContestDisqualificationReason.LOW_EXPOSURE, MarketDatasetKind.GENERAL);
        }
        return null;
    }

    /** 일반장 수익률을 시장 대비로 재서 0~100 으로 옮긴다. */
    public static double returnScore(BigDecimal cumulativeReturn, BigDecimal generalMarketReturn) {
        double excess = cumulativeReturn.doubleValue() - generalMarketReturn.doubleValue();
        return clamp((excess + RETURN_SPAN) / (2 * RETURN_SPAN) * 100);
    }

    /** 구간 최대 낙폭을 그 장세의 시장 낙폭 대비로 재서 0~100 으로 옮긴다. 시장만큼 빠지면 50점이다. */
    public static double regimeScore(BigDecimal maximumDrawdown, BigDecimal marketMaximumDrawdown) {
        double avoided = marketMaximumDrawdown.doubleValue() - maximumDrawdown.doubleValue();
        return clamp((avoided + DRAWDOWN_SPAN) / (2 * DRAWDOWN_SPAN) * 100);
    }

    public static double stabilityScore(List<Double> regimeScores) {
        double mean = regimeScores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double worst = regimeScores.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        return 0.6 * mean + 0.4 * worst;
    }

    public static double totalScore(double returnScore, double stabilityScore) {
        return 0.5 * returnScore + 0.5 * stabilityScore;
    }

    private static List<ContestResult> score(
            List<UUID> qualified,
            Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes,
            BigDecimal generalMarketReturn
    ) {
        if (qualified.isEmpty()) {
            return List.of();
        }

        Map<UUID, Double> returnScores = new LinkedHashMap<>();
        Map<MarketDatasetKind, Map<UUID, Double>> regimeScores = new LinkedHashMap<>();
        for (MarketDatasetKind regime : REGIMES) {
            regimeScores.put(regime, new LinkedHashMap<>());
        }
        Map<UUID, Double> stabilities = new LinkedHashMap<>();
        Map<UUID, Double> totals = new LinkedHashMap<>();

        for (UUID entryId : qualified) {
            Map<MarketDatasetKind, ContestEvaluationOutcome> entryOutcomes = outcomes.get(entryId);
            returnScores.put(entryId, returnScore(
                    entryOutcomes.get(MarketDatasetKind.GENERAL).cumulativeReturn(), generalMarketReturn));

            List<Double> perRegime = new ArrayList<>();
            for (MarketDatasetKind regime : REGIMES) {
                ContestEvaluationOutcome outcome = entryOutcomes.get(regime);
                double score = regimeScore(outcome.maximumDrawdown(), outcome.marketMaximumDrawdown());
                regimeScores.get(regime).put(entryId, score);
                perRegime.add(score);
            }

            double stability = stabilityScore(perRegime);
            stabilities.put(entryId, stability);
            totals.put(entryId, totalScore(returnScores.get(entryId), stability));
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

    private static double clamp(double value) {
        return Math.min(Math.max(value, 0), 100);
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    public record Disqualification(ContestDisqualificationReason reason, MarketDatasetKind kind) {
    }
}
