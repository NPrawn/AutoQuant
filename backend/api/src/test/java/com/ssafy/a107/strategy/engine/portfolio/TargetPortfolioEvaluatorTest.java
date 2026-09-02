package com.ssafy.a107.strategy.engine.portfolio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TargetPortfolioEvaluatorTest {

    private final TargetPortfolioEvaluator evaluator = new TargetPortfolioEvaluator();

    @Test
    @DisplayName("선택된 종목을 동일 비중으로 구성하고 반올림 잔여 비중은 현금으로 유지한다")
    void createEqualWeightPortfolio() {
        // given
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID thirdAssetId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        // when
        TargetPortfolio targetPortfolio = evaluator.create(List.of(firstAssetId, secondAssetId, thirdAssetId));

        // then
        BigDecimal equalWeight = new BigDecimal("0.3333333333");
        assertThat(targetPortfolio.assetWeights()).containsExactly(
                new TargetAssetWeight(firstAssetId, equalWeight),
                new TargetAssetWeight(secondAssetId, equalWeight),
                new TargetAssetWeight(thirdAssetId, equalWeight)
        );
        assertThat(targetPortfolio.cashWeight()).isEqualByComparingTo(new BigDecimal("0.0000000001"));
        assertThat(totalWeight(targetPortfolio)).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    @DisplayName("선택된 종목이 없으면 현금 100퍼센트 목표 포트폴리오를 구성한다")
    void createCashOnlyPortfolioWhenNoAssetIsSelected() {
        // when
        TargetPortfolio targetPortfolio = evaluator.create(List.of());

        // then
        assertThat(targetPortfolio.assetWeights()).isEmpty();
        assertThat(targetPortfolio.cashWeight()).isEqualByComparingTo(BigDecimal.ONE);
    }

    private BigDecimal totalWeight(TargetPortfolio targetPortfolio) {
        BigDecimal assetWeightTotal = BigDecimal.ZERO;
        for (TargetAssetWeight assetWeight : targetPortfolio.assetWeights()) {
            assetWeightTotal = assetWeightTotal.add(assetWeight.targetWeight());
        }
        return assetWeightTotal.add(targetPortfolio.cashWeight());
    }
}
