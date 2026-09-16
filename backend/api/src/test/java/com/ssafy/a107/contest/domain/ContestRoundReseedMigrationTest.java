package com.ssafy.a107.contest.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 1회차를 워밍업 30주 데이터로 바꾸는 마이그레이션이다. V21 은 이미 적용된 파일이라 고치지 않고
 * 같은 회차를 지운 뒤 다시 넣는다 (ADR-0016 결정 6). 파일은 export_seed_sql.py --replace 가 만든다.
 */
class ContestRoundReseedMigrationTest {

    private static final Path MIGRATION_PATH = Path.of(
            "src/main/resources/db/migration/V26__reseed_contest_round_1_warmup_30w.sql"
    );
    private static final String ROUND_ID = "0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10";

    @DisplayName("옛 회차 데이터를 외래 키 순서대로 지운 뒤 다시 넣는다")
    @Test
    void clearTheRoundBeforeInserting() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        int result = migration.indexOf("DELETE FROM contest.contest_result");
        int evaluation = migration.indexOf("DELETE FROM contest.contest_evaluation");
        int backtest = migration.indexOf("DELETE FROM trading.backtest_run");
        int entry = migration.indexOf("DELETE FROM contest.contest_entry");
        int candle = migration.indexOf("DELETE FROM contest.market_candle");
        int datasetAsset = migration.indexOf("DELETE FROM contest.market_dataset_asset");
        int dataset = migration.indexOf("DELETE FROM contest.market_dataset WHERE");
        int round = migration.indexOf("DELETE FROM contest.contest_round");
        int insert = migration.indexOf("INSERT INTO contest.contest_round");

        assertThat(result).isLessThan(evaluation);
        assertThat(evaluation).isLessThan(backtest);
        assertThat(backtest).isLessThan(entry);
        assertThat(entry).isLessThan(candle);
        assertThat(candle).isLessThan(datasetAsset);
        assertThat(datasetAsset).isLessThan(dataset);
        assertThat(dataset).isLessThan(round);
        assertThat(round).isLessThan(insert);
    }

    @DisplayName("같은 회차 ID 를 워밍업 30주로 다시 넣는다")
    @Test
    void reinsertTheSameRoundWithLongerWarmup() throws IOException {
        String migration = Files.readString(MIGRATION_PATH);

        assertThat(migration).contains("'" + ROUND_ID + "', '1회차 모의 대회'");
        assertThat(migration).contains("    5, 30, 13\n");
        assertThat(migration).contains("'READY'");
    }

    @DisplayName("팩터 기간 상한은 새 워밍업에서 나온다")
    @Test
    void capsFollowTheNewWarmup() {
        assertThat(ContestFactorPeriodPolicy.caps(30))
                .containsEntry(com.ssafy.a107.strategy.domain.dsl.Timeframe.ONE_HOUR, 5039)
                .containsEntry(com.ssafy.a107.strategy.domain.dsl.Timeframe.FOUR_HOURS, 1259)
                .containsEntry(com.ssafy.a107.strategy.domain.dsl.Timeframe.ONE_DAY, 209);
    }
}
