package com.ssafy.a107.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 1회차 시드는 ai/scripts/contest_sim/export_seed_sql.py 가 만든다 (ADR-0016). 여기서는 파일이 지켜야 할 모양만 본다.
 */
class ContestRoundSeedMigrationTest {

    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V21__seed_contest_round_1.sql"
    );

    @DisplayName("회차와 데이터셋 4벌을 넣는다")
    @Test
    void seedRoundAndDatasets() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains(
                "INSERT INTO contest.contest_round",
                "INSERT INTO contest.market_dataset",
                "'GENERAL'",
                "'BULL'",
                "'BEAR'",
                "'SIDEWAYS'"
        );
    }

    @DisplayName("종목은 마켓 코드로 붙이고, 모자라면 배포를 멈춘다")
    @Test
    void joinAssetsByMarketCodeAndFailWhenMissing() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains(
                "INSERT INTO reference.asset",
                "ON CONFLICT (exchange, exchange_market_code) DO NOTHING",
                "asset.exchange_market_code = candle.market_code",
                "RAISE EXCEPTION"
        );
    }

    @DisplayName("1시간봉만 넣고 4시간봉과 일봉은 집계한다")
    @Test
    void aggregateHigherIntervalsInsideTheMigration() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains(
                "SELECT '",
                "60, candle.open_time::timestamptz",
                "       240,",
                "       1440,",
                "array_agg(hourly.open_price ORDER BY hourly.open_time)",
                "array_agg(hourly.close_price ORDER BY hourly.open_time DESC)"
        );
        assertThat(migration).doesNotContain("240, candle.open_time::timestamptz");
    }
}
