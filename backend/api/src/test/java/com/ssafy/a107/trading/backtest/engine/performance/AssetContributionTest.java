package com.ssafy.a107.trading.backtest.engine.performance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AssetContributionTest {

    private static final UUID ASSET_ID = UUID.fromString(
            "c56f7340-3dcc-41b8-a590-ef347dc53492"
    );

    @DisplayName("자산별 순손익과 기여율로 종목 기여도를 생성한다")
    @Test
    void createAssetContribution() {
        AssetContribution contribution = new AssetContribution(
                ASSET_ID,
                new BigDecimal("100000"),
                new BigDecimal("0.1")
        );

        assertThat(contribution.assetId()).isEqualTo(ASSET_ID);
        assertThat(contribution.profitLoss())
                .isEqualByComparingTo("100000");
        assertThat(contribution.contributionRate())
                .isEqualByComparingTo("0.1");
    }

    @DisplayName("종목 기여도의 자산 식별자는 null일 수 없다")
    @Test
    void rejectNullAssetId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AssetContribution(
                        null,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("종목 기여도의 순손익은 null일 수 없다")
    @Test
    void rejectNullProfitLoss() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AssetContribution(
                        ASSET_ID,
                        null,
                        BigDecimal.ZERO
                ));
    }

    @DisplayName("종목 기여율은 null일 수 없다")
    @Test
    void rejectNullContributionRate() {
        assertThatNullPointerException()
                .isThrownBy(() -> new AssetContribution(
                        ASSET_ID,
                        BigDecimal.ZERO,
                        null
                ));
    }
}
