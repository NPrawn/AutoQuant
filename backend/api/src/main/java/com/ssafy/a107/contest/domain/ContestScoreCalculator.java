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
 * - 실격: 데이터셋을 일반장·상승장·하락장·횡보장 순으로 보며 처음 실패한 백테스트 하나뿐이다. 크게 잃거나
 *   깊이 빠지거나 거의 사지 않은 전략은 실격이 아니라 아래 점수가 내려간다.
 * - 수익률 점수: 일반장 누적 수익률을 원금 기준 로그로 잰다. 본전이 0점, 자산 3배가 100점, 3분의 1토막이
 *   −100점이고 −200점에서 자른다. 위아래로 갇히지 않은 양이라 로그로 눌러야 꼬리가 다른 축을 삼키지 않는다.
 * - 국면 점수: 구간 최대 낙폭을 그 장세에서 같은 비중으로 시장을 든 경우와 견준다. 시장과 같으면 50점,
 *   절반만 빠지면 75점, 두 배면 0점이다. 투자 비중으로 나누는 것이 핵심이다. 덜 사면 낙폭도 그만큼 작아지므로,
 *   나누지 않으면 덜 사는 것만으로 점수가 오른다.
 * - 안정성 점수: 상승·하락·횡보 국면 점수의 평균 × 0.6 + 최저 × 0.4. 일반장 평균 투자 비중이 하한 미만이면
 *   세 국면 점수를 모두 50점으로 둔다. 분모가 작아 점수가 뒤집히는 구간이라, 비중이 0일 때 쓰는 규칙을
 *   "사실상 미참여"까지 넓힌 것이다.
 * - 총점: 수익률 점수 × 0.5 + 안정성 점수 × 0.5. 강세 회차에서는 100점을 넘고 하락 회차에서는 음수일 수 있다.
 *
 * 척도 상수는 모두 배수다. 퍼센트포인트는 시장 수익률이 회차마다 달라 뜻이 같이 달라진다.
 *
 * 같은 규칙의 참고 구현이 ai/scripts/contest_sim/scoring.py 다.
 */
public final class ContestScoreCalculator {

    private static final List<MarketDatasetKind> REGIMES =
            List.of(MarketDatasetKind.BULL, MarketDatasetKind.BEAR, MarketDatasetKind.SIDEWAYS);
    private static final int SCORE_SCALE = 3;
    /* 수익률 점수의 척도. 자산이 이 배수가 되면 100점이다. */
    private static final double RETURN_MULTIPLE = 3.0;
    /* 수익률 점수의 하한. 자산이 9분의 1 아래로 내려간 구간이라 순위에 닿지 않는다. */
    private static final double RETURN_FLOOR = -200.0;
    /* 국면 점수의 척도. 같은 비중으로 시장을 든 경우의 이 배수만큼 빠지면 0점이다. */
    private static final double DRAWDOWN_MULTIPLE = 2.0;
    /* 그 장세에서 아무것도 사지 않았을 때의 국면 점수. 노출을 줄여도 비율이 1인 식의 극한이다. */
    private static final double NO_EXPOSURE_REGIME_SCORE = 50.0;

    private ContestScoreCalculator() {
    }

    public static List<ContestResult> score(
            Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes
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

        List<ContestResult> results = new ArrayList<>(score(qualified, outcomes));
        results.addAll(disqualifiedResults.values());
        return List.copyOf(results);
    }

    /*
     * 실격 사유와 걸린 데이터셋. 통과하면 null 이다. 마감 전 내 성적 화면도 이 판정을 쓴다.
     *
     * 점수를 낼 수 없는 경우만 실격이다. 투자 비중이 낮은 제출은 실격 대신 국면 점수가 중립이 된다
     * (ADR-0003). 결과를 통째로 감추는 것보다 점수로 드러내는 편이 읽힌다.
     */
    public static Disqualification disqualification(Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes) {
        for (MarketDatasetKind kind : MarketDatasetKind.values()) {
            ContestEvaluationOutcome outcome = outcomes.get(kind);
            if (outcome == null || !outcome.completed()) {
                return new Disqualification(ContestDisqualificationReason.BACKTEST_FAILED, kind);
            }
        }
        return null;
    }

    /*
     * 일반장 평균 투자 비중이 하한 미만이면 국면 점수를 중립으로 둔다.
     *
     * 국면 점수의 분모가 투자 비중이라 비중이 작으면 점수가 뒤집힌다. 비중 2%에 낙폭 0.1% 면 93.75점이
     * 되어, 거의 사지 않은 제출이 실제로 투자한 제출을 이긴다. 비중이 정확히 0일 때 50점을 주는 규칙을
     * 그 구간까지 넓혀 막는다.
     */
    public static boolean tooSmallToMeasure(Map<MarketDatasetKind, ContestEvaluationOutcome> outcomes) {
        BigDecimal exposure = outcomes.get(MarketDatasetKind.GENERAL).averageExposure();
        return exposure == null || exposure.compareTo(ContestScoringPolicy.MINIMUM_EXPOSURE) < 0;
    }

    /** 일반장 수익률을 원금 기준 로그로 잰다. 본전 0점, 자산 3배 100점이고 위로는 갇히지 않는다. */
    public static double returnScore(BigDecimal cumulativeReturn) {
        double growth = 1 + cumulativeReturn.doubleValue();
        if (growth <= 0) {
            return RETURN_FLOOR;
        }
        return Math.max(RETURN_FLOOR, 100 * Math.log(growth) / Math.log(RETURN_MULTIPLE));
    }

    /*
     * 구간 최대 낙폭을 같은 비중으로 시장을 든 경우와 견줘 0~100 으로 옮긴다. 시장만큼 빠지면 50점이다.
     *
     * 투자 비중이 0이면 50점이다. 노출 x 로 시장을 그대로 들면 낙폭도 x 배라 비율이 x 와 무관하게 1 이므로,
     * 0 은 이 식의 극한이지 예외가 아니다. 시장 낙폭이 0 인 데이터셋은 나오지 않지만 분모라서 같이 막는다.
     */
    public static double regimeScore(
            BigDecimal maximumDrawdown,
            BigDecimal averageExposure,
            BigDecimal marketMaximumDrawdown
    ) {
        double exposure = averageExposure == null ? 0 : averageExposure.doubleValue();
        double market = marketMaximumDrawdown.doubleValue();
        if (exposure <= 0 || market <= 0) {
            return NO_EXPOSURE_REGIME_SCORE;
        }
        double ratio = maximumDrawdown.doubleValue() / (exposure * market);
        return clamp(100 * (1 - ratio / DRAWDOWN_MULTIPLE));
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
            Map<UUID, Map<MarketDatasetKind, ContestEvaluationOutcome>> outcomes
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
            returnScores.put(entryId,
                    returnScore(entryOutcomes.get(MarketDatasetKind.GENERAL).cumulativeReturn()));

            boolean neutral = tooSmallToMeasure(entryOutcomes);
            List<Double> perRegime = new ArrayList<>();
            for (MarketDatasetKind regime : REGIMES) {
                ContestEvaluationOutcome outcome = entryOutcomes.get(regime);
                double score = neutral ? NO_EXPOSURE_REGIME_SCORE : regimeScore(
                        outcome.maximumDrawdown(), outcome.averageExposure(), outcome.marketMaximumDrawdown());
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
            /* 점수를 내는 데는 안 쓰고 순위표가 읽히게 같이 적어 둔다. */
            ContestEvaluationOutcome general = outcomes.get(entryId).get(MarketDatasetKind.GENERAL);
            results.add(ContestResult.scored(
                    entryId,
                    scale(returnScores.get(entryId)),
                    scale(regimeScores.get(MarketDatasetKind.BULL).get(entryId)),
                    scale(regimeScores.get(MarketDatasetKind.BEAR).get(entryId)),
                    scale(regimeScores.get(MarketDatasetKind.SIDEWAYS).get(entryId)),
                    scale(stabilities.get(entryId)),
                    scale(totals.get(entryId)),
                    general.cumulativeReturn(),
                    general.maximumDrawdown(),
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
