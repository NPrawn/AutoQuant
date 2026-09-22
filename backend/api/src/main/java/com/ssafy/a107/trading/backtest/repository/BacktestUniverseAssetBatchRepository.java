package com.ssafy.a107.trading.backtest.repository;

import com.ssafy.a107.trading.backtest.domain.BacktestUniverseAsset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class BacktestUniverseAssetBatchRepository {

    static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL = """
            INSERT INTO trading.backtest_universe_asset (
                backtest_id,
                rebalance_at,
                asset_id,
                selection_rank,
                is_selected
            ) VALUES (?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public BacktestUniverseAssetBatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertAll(List<BacktestUniverseAsset> universeAssets) {
        if (universeAssets.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                universeAssets,
                BATCH_SIZE,
                (statement, universeAsset) -> {
                    var id = universeAsset.getId();
                    statement.setObject(1, id.getBacktestId());
                    statement.setObject(
                            2,
                            OffsetDateTime.ofInstant(
                                    id.getRebalanceAt(),
                                    ZoneOffset.UTC
                            )
                    );
                    statement.setObject(3, id.getAssetId());

                    if (universeAsset.getSelectionRank() == null) {
                        statement.setNull(4, Types.SMALLINT);
                    } else {
                        statement.setShort(4, universeAsset.getSelectionRank());
                    }

                    statement.setBoolean(5, universeAsset.isSelected());
                }
        );
    }
}
