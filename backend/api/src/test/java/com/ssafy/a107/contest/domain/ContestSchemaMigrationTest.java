package com.ssafy.a107.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContestSchemaMigrationTest {

    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V20__create_contest_tables.sql"
    );

    @DisplayName("대회 회차와 데이터셋 테이블을 contest 스키마에 만든다")
    @Test
    void createContestTables() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains(
                "CREATE SCHEMA IF NOT EXISTS contest",
                "CREATE TABLE contest.contest_round",
                "CREATE TABLE contest.market_dataset",
                "CREATE TABLE contest.market_dataset_asset",
                "CREATE TABLE contest.market_candle",
                "ck_contest_round_phase CHECK (phase IN ('READY', 'SCORING', 'FINISHED'))",
                "uq_market_dataset_round_kind UNIQUE (round_id, kind)",
                "ck_market_dataset_kind CHECK (kind IN ('GENERAL', 'BULL', 'BEAR', 'SIDEWAYS'))",
                "ck_market_candle_interval_minutes CHECK (interval_minutes IN (60, 240, 1440))"
        );
    }

    @DisplayName("대회 캔들 값 열은 확정 분봉과 같은 타입과 제약을 쓴다")
    @Test
    void keepCandleColumnTypesInSyncWithMinuteCandle() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains(
                "open_price        NUMERIC(30,12) NOT NULL",
                "trade_volume      NUMERIC(38,18) NOT NULL",
                "trade_amount      NUMERIC(38,8)  NOT NULL",
                "ck_market_candle_prices",
                "pk_market_candle PRIMARY KEY (dataset_id, asset_id, interval_minutes, open_time)"
        );
    }

    @DisplayName("백테스트 실행은 대회 데이터셋을 가리킬 수 있다")
    @Test
    void linkBacktestRunToContestDataset() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains(
                "ADD COLUMN contest_dataset_id UUID NULL",
                "fk_backtest_run_contest_dataset FOREIGN KEY (contest_dataset_id)",
                "idx_backtest_run_contest_dataset_id"
        );
    }
}
