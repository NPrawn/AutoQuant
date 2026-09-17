package com.ssafy.a107.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContestMarketDrawdownMigrationTest {

    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V28__contest_market_drawdown.sql"
    );

    @DisplayName("1회차 데이터셋 네 벌에 봉 간격별 시장 낙폭을 채우고 빈 값을 막는다")
    @Test
    void fillMarketDrawdownsForRoundOne() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains(
                "('GENERAL',  0.38448381, 0.38448381, 0.32298406)",
                "('BULL',     0.36501658, 0.35704722, 0.35360052)",
                "('BEAR',     0.51515961, 0.50414435, 0.49958768)",
                "('SIDEWAYS', 0.39248419, 0.39028694, 0.35546703)",
                "ALTER COLUMN market_mdd_1d SET NOT NULL"
        );
    }

    @DisplayName("산식이 바뀌어 저장된 점수를 지운다")
    @Test
    void clearScoresForRescoring() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration.indexOf("DELETE FROM contest.contest_result"))
                .isGreaterThan(migration.indexOf("SET NOT NULL"));
    }
}
