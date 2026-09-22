package com.ssafy.a107.trading.backtest.domain;

import com.ssafy.a107.asset.domain.Asset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class BacktestUniverseAssetTest {

    @DisplayName("새 후보군 종목은 신규 영속 엔티티로 판단한다")
    @Test
    void createNewUniverseAsset() {
        UUID backtestId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID assetId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        BacktestUniverseAsset universeAsset = new BacktestUniverseAsset(
                new BacktestUniverseAssetId(
                        backtestId,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        assetId
                ),
                mock(BacktestRun.class),
                mock(Asset.class),
                (short) 1,
                true
        );

        assertThat(universeAsset.isNew()).isTrue();
    }
}
