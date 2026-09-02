package com.ssafy.a107.strategy.engine.ranking;

import com.ssafy.a107.strategy.domain.dsl.FactorOperand;
import com.ssafy.a107.strategy.domain.dsl.Ranking;
import com.ssafy.a107.strategy.domain.dsl.RankingDirection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class RankingEvaluator {

    public List<UUID> rank(Map<UUID, Map<FactorOperand, BigDecimal>> assetFactorValues, Ranking ranking) {
        Objects.requireNonNull(assetFactorValues);
        Objects.requireNonNull(ranking);

        FactorOperand rankingFactor = new FactorOperand(ranking.factor(), ranking.period());
        List<RankedAsset> rankableAssets = new ArrayList<>();
        for (Map.Entry<UUID, Map<FactorOperand, BigDecimal>> assetEntry : assetFactorValues.entrySet()) {
            BigDecimal rankingValue = assetEntry.getValue().get(rankingFactor);
            if (rankingValue != null) {
                rankableAssets.add(new RankedAsset(assetEntry.getKey(), rankingValue));
            }
        }

        rankableAssets.sort(new RankingComparator(ranking.direction()));

        List<UUID> rankedAssetIds = new ArrayList<>(rankableAssets.size());
        for (RankedAsset rankedAsset : rankableAssets) {
            rankedAssetIds.add(rankedAsset.assetId());
        }
        return List.copyOf(rankedAssetIds);
    }

    private static class RankingComparator implements Comparator<RankedAsset> {

        private final RankingDirection direction;

        private RankingComparator(RankingDirection direction) {
            this.direction = direction;
        }

        @Override
        public int compare(RankedAsset leftAsset, RankedAsset rightAsset) {
            int valueComparison = leftAsset.rankingValue().compareTo(rightAsset.rankingValue());

            if (valueComparison != 0) {
                return compareByDirection(valueComparison);
            }
            return leftAsset.assetId().compareTo(rightAsset.assetId());
        }

        private int compareByDirection(int valueComparison) {
            if (direction == RankingDirection.DESC) {
                return -valueComparison;
            }
            return valueComparison;
        }
    }

    private record RankedAsset(UUID assetId, BigDecimal rankingValue) {
    }
}
