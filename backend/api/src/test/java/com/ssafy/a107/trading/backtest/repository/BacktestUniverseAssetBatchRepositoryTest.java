package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAsset;
import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAssetId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BacktestUniverseAssetBatchRepositoryTest {

    private static final UUID BACKTEST_ID = UUID.fromString(
            "11111111-1111-1111-1111-111111111111"
    );
    private static final UUID ASSET_ID = UUID.fromString(
            "22222222-2222-2222-2222-222222222222"
    );
    private static final Instant REBALANCE_AT = Instant.parse("2026-01-02T00:00:00Z");

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PreparedStatement statement;

    @Mock
    private BacktestRun backtestRun;

    @Mock
    private Asset asset;

    @Captor
    private ArgumentCaptor<ParameterizedPreparedStatementSetter<BacktestUniverseAsset>> setterCaptor;

    @DisplayName("후보군 종목을 JDBC 배치 INSERT로 저장한다")
    @Test
    void insertUniverseAssetsInBatches() throws Exception {
        BacktestUniverseAsset universeAsset = universeAsset((short) 1, true);

        repository().insertAll(List.of(universeAsset));

        then(jdbcTemplate).should().batchUpdate(
                any(String.class),
                eq(List.of(universeAsset)),
                eq(BacktestUniverseAssetBatchRepository.BATCH_SIZE),
                setterCaptor.capture()
        );
        setterCaptor.getValue().setValues(statement, universeAsset);

        then(statement).should().setObject(1, BACKTEST_ID);
        then(statement).should().setObject(
                2,
                OffsetDateTime.ofInstant(REBALANCE_AT, ZoneOffset.UTC)
        );
        then(statement).should().setObject(3, ASSET_ID);
        then(statement).should().setShort(4, (short) 1);
        then(statement).should().setBoolean(5, true);
    }

    @DisplayName("선정 순위가 없으면 NULL로 저장한다")
    @Test
    void insertNullSelectionRank() throws Exception {
        BacktestUniverseAsset universeAsset = universeAsset(null, false);

        repository().insertAll(List.of(universeAsset));

        then(jdbcTemplate).should().batchUpdate(
                any(String.class),
                eq(List.of(universeAsset)),
                eq(BacktestUniverseAssetBatchRepository.BATCH_SIZE),
                setterCaptor.capture()
        );
        setterCaptor.getValue().setValues(statement, universeAsset);

        then(statement).should().setNull(4, Types.SMALLINT);
        then(statement).should().setBoolean(5, false);
    }

    @DisplayName("저장할 후보군 종목이 없으면 JDBC 호출을 생략한다")
    @Test
    void skipEmptyUniverseAssets() {
        repository().insertAll(List.of());

        then(jdbcTemplate).shouldHaveNoInteractions();
    }

    private BacktestUniverseAssetBatchRepository repository() {
        return new BacktestUniverseAssetBatchRepository(jdbcTemplate);
    }

    private BacktestUniverseAsset universeAsset(Short selectionRank, boolean selected) {
        return new BacktestUniverseAsset(
                new BacktestUniverseAssetId(BACKTEST_ID, REBALANCE_AT, ASSET_ID),
                backtestRun,
                asset,
                selectionRank,
                selected
        );
    }
}
