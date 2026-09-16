package com.ssafy.a107.candle.repository;

import com.ssafy.a107.candle.domain.MinuteCandle;
import com.ssafy.a107.candle.domain.MinuteCandleCoverage;
import com.ssafy.a107.candle.domain.MinuteCandleSnapshot;
import com.ssafy.a107.candle.service.CandleCoverageReadService;
import com.ssafy.a107.candle.service.MinuteCandleReadService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:${CANDLE_TEST_DB_PORT}/candle_test",
        "spring.datasource.username=candle_test",
        "spring.datasource.password=candle_test",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = MinuteCandleRepositoryIntegrationTest.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "CANDLE_TEST_DB_PORT", matches = "[0-9]+")
class MinuteCandleRepositoryIntegrationTest {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final MinuteCandleReadService service;
    private final CandleCoverageReadService coverageReadService;

    @Autowired
    MinuteCandleRepositoryIntegrationTest(JdbcTemplate jdbc, EntityManager entityManager,
            MinuteCandleReadService service, CandleCoverageReadService coverageReadService) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.service = service;
        this.coverageReadService = coverageReadService;
    }

    @Test
    @DisplayName("PostgreSQL에서 해당 종목의 1분봉만 최신 1000개 조회하고 오름차순으로 반환한다")
    void readLatestThousandForOneAsset() {
        UUID bitcoin = UUID.randomUUID();
        UUID ethereum = UUID.randomUUID();
        insertAsset(bitcoin, "BTC");
        insertAsset(ethereum, "ETH");
        insertCandles(bitcoin, 1, 1002);
        insertCandles(bitcoin, 5, 1);
        insertCandles(ethereum, 1, 1);
        entityManager.clear();

        List<MinuteCandleSnapshot> candles = service.findRecent(bitcoin, 1);

        assertThat(candles).hasSize(1000);
        assertThat(candles).allMatch(candle -> candle.assetId().equals(bitcoin));
        long start = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        assertThat(candles.getFirst().openTimeUnixMilli()).isEqualTo(start + 2 * 60000L);
        assertThat(candles.getLast().openTimeUnixMilli()).isEqualTo(start + 1001 * 60000L);
        assertThat(candles.getFirst().openPrice()).isEqualTo("100.123456789012");
        assertThat(candles.getFirst().tradeVolume()).isEqualTo("0.123456789012345678");
        assertThat(candles.getFirst().tradeAmount()).isEqualTo("12.12345678");
    }

    @Test
    @DisplayName("조회 간격이 다른 봉과 다른 종목의 봉을 섞지 않는다")
    void readSelectedInterval() {
        UUID bitcoin = UUID.randomUUID();
        UUID ethereum = UUID.randomUUID();
        insertAsset(bitcoin, "BTC");
        insertAsset(ethereum, "ETH");
        insertCandles(bitcoin, 1, 3);
        insertCandles(bitcoin, 5, 1);
        insertCandles(ethereum, 5, 2);
        entityManager.clear();

        assertThat(service.findRecent(bitcoin, 5)).singleElement()
                .satisfies(candle -> assertThat(candle.assetId()).isEqualTo(bitcoin));
    }

    @Test
    @DisplayName("봉이 없는 자산 UUID 조회는 빈 목록을 반환한다")
    void readEmptyHistory() {
        assertThat(service.findRecent(UUID.randomUUID(), 1)).isEmpty();
    }

    private void insertAsset(UUID assetId, String currency) {
        jdbc.update("""
                INSERT INTO reference.asset (
                    asset_id, exchange, exchange_market_code, base_currency_code,
                    quote_currency_code, korean_name, english_name, trading_status
                ) VALUES (?, 'UPBIT', ?, ?, 'KRW', ?, ?, 'TRADING')
                """, assetId, "KRW-" + currency, currency, currency, currency);
    }

    private void insertCandles(UUID assetId, int interval, int count) {
        jdbc.update("""
                INSERT INTO market_data.minute_candle (
                    asset_id, interval_minutes, open_time, open_price, high_price, low_price, close_price,
                    trade_volume, trade_amount, last_event_time
                )
                SELECT ?, ?, TIMESTAMPTZ '2026-01-01 00:00:00+00' + n * INTERVAL '1 minute',
                    100.123456789012, 101, 99, 100, 0.123456789012345678, 12.12345678,
                    TIMESTAMPTZ '2026-01-01 00:00:59+00' + n * INTERVAL '1 minute'
                FROM generate_series(0, ?) AS n
                """, assetId, interval, count - 1);
    }

    @Test
    @DisplayName("보유 구간은 대상 종목을 합쳐 가장 이른 봉과 가장 늦은 봉의 시작 시각이다")
    void readCoverageAcrossAssets() {
        UUID bitcoin = UUID.randomUUID();
        UUID ethereum = UUID.randomUUID();
        UUID excluded = UUID.randomUUID();
        insertAsset(bitcoin, "BTC");
        insertAsset(ethereum, "ETH");
        insertAsset(excluded, "XRP");
        insertCandles(bitcoin, 1, 3);
        insertCandlesFrom(ethereum, 1, 2, "2025-12-31 23:00:00+00");
        insertCandlesFrom(excluded, 1, 2, "2020-01-01 00:00:00+00");
        entityManager.clear();

        MinuteCandleCoverage coverage = coverageReadService.findCoverage(1, List.of(bitcoin, ethereum));

        assertThat(coverage.oldestOpenTime()).isEqualTo(Instant.parse("2025-12-31T23:00:00Z"));
        assertThat(coverage.newestOpenTime()).isEqualTo(Instant.parse("2026-01-01T00:02:00Z"));
    }

    @Test
    @DisplayName("보유 구간은 요청한 간격의 봉만 본다")
    void readCoverageOfSelectedInterval() {
        UUID bitcoin = UUID.randomUUID();
        insertAsset(bitcoin, "BTC");
        insertCandles(bitcoin, 1, 3);
        insertCandlesFrom(bitcoin, 5, 1, "2019-01-01 00:00:00+00");
        entityManager.clear();

        MinuteCandleCoverage coverage = coverageReadService.findCoverage(5, List.of(bitcoin));

        assertThat(coverage.oldestOpenTime()).isEqualTo(Instant.parse("2019-01-01T00:00:00Z"));
        assertThat(coverage.newestOpenTime()).isEqualTo(Instant.parse("2019-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("봉이 없는 종목만 있으면 보유 구간이 비어 있다")
    void readEmptyCoverage() {
        assertThat(coverageReadService.findCoverage(1, List.of(UUID.randomUUID())).isEmpty()).isTrue();
    }

    private void insertCandlesFrom(UUID assetId, int interval, int count, String startAt) {
        jdbc.update("""
                INSERT INTO market_data.minute_candle (
                    asset_id, interval_minutes, open_time, open_price, high_price, low_price, close_price,
                    trade_volume, trade_amount, last_event_time
                )
                SELECT ?, ?, CAST(? AS TIMESTAMPTZ) + n * make_interval(mins => ?),
                    100.123456789012, 101, 99, 100, 0.123456789012345678, 12.12345678,
                    CAST(? AS TIMESTAMPTZ) + n * make_interval(mins => ?)
                FROM generate_series(0, ?) AS n
                """, assetId, interval, startAt, interval, startAt, interval, count - 1);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableJpaRepositories(basePackageClasses = MinuteCandleRepository.class)
    @EntityScan(basePackageClasses = MinuteCandle.class)
    @Import({MinuteCandleReadService.class, CandleCoverageReadService.class})
    static class TestConfiguration {
    }
}
