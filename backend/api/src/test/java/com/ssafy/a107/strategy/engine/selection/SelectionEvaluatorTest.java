package com.ssafy.a107.strategy.engine.selection;

import com.ssafy.a107.strategy.domain.dsl.Selection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SelectionEvaluatorTest {

    private final SelectionEvaluator evaluator = new SelectionEvaluator();

    @Test
    @DisplayName("Ranking 결과의 앞에서부터 topN개 종목을 선택한다")
    void selectTopNAssets() {
        // given
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID thirdAssetId = UUID.fromString("00000000-0000-0000-0000-000000000003");

        // when
        var selectedAssetIds = evaluator.select(List.of(firstAssetId, secondAssetId, thirdAssetId), new Selection(2));

        // then
        assertThat(selectedAssetIds).containsExactly(firstAssetId, secondAssetId);
    }

    @Test
    @DisplayName("Ranking 결과가 topN보다 적으면 존재하는 종목만 선택한다")
    void selectAllAssetsWhenRankingResultIsSmallerThanTopN() {
        // given
        UUID firstAssetId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAssetId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // when
        var selectedAssetIds = evaluator.select(List.of(firstAssetId, secondAssetId), new Selection(5));

        // then
        assertThat(selectedAssetIds).containsExactly(firstAssetId, secondAssetId);
    }
}
