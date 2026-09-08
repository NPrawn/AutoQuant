package com.ssafy.a107.trading.backtest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestUniverseAssetPersistenceMappingTest {

    @Test
    void mapBacktestUniverseAssetTable() {
        Table table = BacktestUniverseAsset.class.getAnnotation(Table.class);

        assertThat(table.schema()).isEqualTo("trading");
        assertThat(table.name()).isEqualTo("backtest_universe_asset");
    }

    @Test
    void mapCompositeId() throws NoSuchFieldException {
        Field idField = BacktestUniverseAsset.class.getDeclaredField("id");

        assertThat(idField.getAnnotation(EmbeddedId.class)).isNotNull();
        assertThat(idField.getType()).isEqualTo(BacktestUniverseAssetId.class);
    }

    @Test
    void mapBacktestRunRelationship() throws NoSuchFieldException {
        Field field = BacktestUniverseAsset.class.getDeclaredField("backtestRun");
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        MapsId mapsId = field.getAnnotation(MapsId.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(manyToOne.optional()).isFalse();
        assertThat(mapsId.value()).isEqualTo("backtestId");
        assertThat(joinColumn.name()).isEqualTo("backtest_id");
        assertThat(joinColumn.foreignKey().name())
                .isEqualTo("fk_backtest_universe_asset_run");
    }

    @Test
    void mapAssetRelationship() throws NoSuchFieldException {
        Field field = BacktestUniverseAsset.class.getDeclaredField("asset");
        ManyToOne manyToOne = field.getAnnotation(ManyToOne.class);
        MapsId mapsId = field.getAnnotation(MapsId.class);
        JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);

        assertThat(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(manyToOne.optional()).isFalse();
        assertThat(mapsId.value()).isEqualTo("assetId");
        assertThat(joinColumn.name()).isEqualTo("asset_id");
        assertThat(joinColumn.foreignKey().name())
                .isEqualTo("fk_backtest_universe_asset_asset");
    }

    @Test
    void mapSelectionFields() throws NoSuchFieldException {
        Field selectionRank = BacktestUniverseAsset.class
                .getDeclaredField("selectionRank");
        Field selected = BacktestUniverseAsset.class
                .getDeclaredField("selected");

        Column selectionRankColumn = selectionRank.getAnnotation(Column.class);
        Column selectedColumn = selected.getAnnotation(Column.class);

        assertThat(selectionRankColumn.name()).isEqualTo("selection_rank");
        assertThat(selectedColumn.name()).isEqualTo("is_selected");
        assertThat(selectedColumn.nullable()).isFalse();
    }
}
