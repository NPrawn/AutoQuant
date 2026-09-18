package com.ssafy.a107.contest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class ContestPersistenceMappingTest {

    @DisplayName("대회 엔티티는 contest 스키마의 테이블에 매핑한다")
    @Test
    void mapContestTables() {
        assertTable(ContestRound.class, "contest_round");
        assertTable(MarketDataset.class, "market_dataset");
        assertTable(MarketDatasetAsset.class, "market_dataset_asset");
        assertTable(ContestCandle.class, "market_candle");
    }

    @DisplayName("데이터셋은 회차를 지연 로딩 다대일 관계로 매핑한다")
    @Test
    void mapDatasetRoundAsLazyManyToOne() throws NoSuchFieldException {
        Field roundField = MarketDataset.class.getDeclaredField("round");

        ManyToOne manyToOne = roundField.getAnnotation(ManyToOne.class);
        JoinColumn joinColumn = roundField.getAnnotation(JoinColumn.class);

        assertThat(manyToOne.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(manyToOne.optional()).isFalse();
        assertThat(joinColumn.name()).isEqualTo("round_id");
        assertThat(joinColumn.foreignKey().name()).isEqualTo("fk_market_dataset_round");
    }

    @DisplayName("데이터셋 종목과 대회 캔들은 복합 키를 쓴다")
    @Test
    void mapCompositeKeys() throws NoSuchFieldException {
        assertThat(MarketDatasetAsset.class.getDeclaredField("id").getAnnotation(EmbeddedId.class)).isNotNull();
        assertThat(ContestCandle.class.getDeclaredField("id").getAnnotation(EmbeddedId.class)).isNotNull();
        assertThat(MarketDatasetAssetId.class.getDeclaredField("assetId").getAnnotation(Column.class).name())
                .isEqualTo("asset_id");
        assertThat(ContestCandleId.class.getDeclaredField("intervalMinutes").getAnnotation(Column.class).name())
                .isEqualTo("interval_minutes");
    }

    @DisplayName("대회 캔들은 읽기 전용이고 값 열의 자릿수는 확정 분봉과 같다")
    @Test
    void mapCandleValuesLikeMinuteCandle() throws NoSuchFieldException {
        assertThat(ContestCandle.class.getAnnotation(Immutable.class)).isNotNull();

        Column closePrice = ContestCandle.class.getDeclaredField("closePrice").getAnnotation(Column.class);
        Column tradeVolume = ContestCandle.class.getDeclaredField("tradeVolume").getAnnotation(Column.class);
        Column tradeAmount = ContestCandle.class.getDeclaredField("tradeAmount").getAnnotation(Column.class);

        assertThat(closePrice.precision()).isEqualTo(30);
        assertThat(closePrice.scale()).isEqualTo(12);
        assertThat(tradeVolume.precision()).isEqualTo(38);
        assertThat(tradeVolume.scale()).isEqualTo(18);
        assertThat(tradeAmount.precision()).isEqualTo(38);
        assertThat(tradeAmount.scale()).isEqualTo(8);
    }

    private static void assertTable(Class<?> entityType, String tableName) {
        Table table = entityType.getAnnotation(Table.class);

        assertThat(table.schema()).isEqualTo("contest");
        assertThat(table.name()).isEqualTo(tableName);
    }
}
