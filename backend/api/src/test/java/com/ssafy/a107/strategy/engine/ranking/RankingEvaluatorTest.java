package com.ssafy.a107.strategy.engine.ranking;

import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RankingEvaluatorTest {

    private final RankingEvaluator evaluator = new RankingEvaluator();

    @Test
    @DisplayName("DESC 방향으로 Ranking Factor 값이 높은 종목부터 정렬한다")
    void rankDescending() {
        // given
        FactorOperand rankingFactor = new FactorOperand(FactorType.RETURN, 20);
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID thirdAssetId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues = Map.of(
                firstAssetId, Map.of(rankingFactor, new BigDecimal("10")),
                secondAssetId, Map.of(rankingFactor, new BigDecimal("20")),
                thirdAssetId, Map.of(rankingFactor, new BigDecimal("5"))
        );

        // when
        var rankedAssetIds = evaluator.rank(assetFactorValues, ranking(RankingDirection.DESC));

        // then
        assertThat(rankedAssetIds).containsExactly(secondAssetId, firstAssetId, thirdAssetId);
    }

    @Test
    @DisplayName("ASC 방향으로 Ranking Factor 값이 낮은 종목부터 정렬한다")
    void rankAscending() {
        // given
        FactorOperand rankingFactor = new FactorOperand(FactorType.RETURN, 20);
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues = Map.of(
                firstAssetId, Map.of(rankingFactor, new BigDecimal("10")),
                secondAssetId, Map.of(rankingFactor, new BigDecimal("5"))
        );

        // when
        var rankedAssetIds = evaluator.rank(assetFactorValues, ranking(RankingDirection.ASC));

        // then
        assertThat(rankedAssetIds).containsExactly(secondAssetId, firstAssetId);
    }

    @Test
    @DisplayName("Ranking Factor 계산값이 없는 종목은 정렬 결과에서 제외한다")
    void excludeAssetWithoutRankingFactor() {
        // given
        FactorOperand rankingFactor = new FactorOperand(FactorType.RETURN, 20);
        UUID calculatedAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID missingAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues = Map.of(
                calculatedAssetId, Map.of(rankingFactor, BigDecimal.TEN),
                missingAssetId, Map.of(new FactorOperand(FactorType.RSI, 14), new BigDecimal("30"))
        );

        // when
        var rankedAssetIds = evaluator.rank(assetFactorValues, ranking(RankingDirection.DESC));

        // then
        assertThat(rankedAssetIds).containsExactly(calculatedAssetId);
    }

    @Test
    @DisplayName("동일한 Ranking Factor 값은 assetId 오름차순으로 정렬한다")
    void rankTiesByAssetId() {
        // given
        FactorOperand rankingFactor = new FactorOperand(FactorType.RETURN, 20);
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues = Map.of(
                secondAssetId, Map.of(rankingFactor, BigDecimal.TEN),
                firstAssetId, Map.of(rankingFactor, BigDecimal.TEN)
        );

        // when
        var rankedAssetIds = evaluator.rank(assetFactorValues, ranking(RankingDirection.DESC));

        // then
        assertThat(rankedAssetIds).containsExactly(firstAssetId, secondAssetId);
    }

    private Ranking ranking(RankingDirection direction) {
        return new Ranking(FactorType.RETURN, 20, direction);
    }

}
