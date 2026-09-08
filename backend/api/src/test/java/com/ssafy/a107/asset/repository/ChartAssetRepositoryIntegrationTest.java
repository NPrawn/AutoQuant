package com.ssafy.a107.asset.repository;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.service.AssetReadService;
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
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:${CANDLE_TEST_DB_PORT}/candle_test",
        "spring.datasource.username=candle_test", "spring.datasource.password=candle_test",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = ChartAssetRepositoryIntegrationTest.TestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "CANDLE_TEST_DB_PORT", matches = "[0-9]+")
class ChartAssetRepositoryIntegrationTest {
    private final EntityManager entityManager;
    private final AssetReadService service;

    @Autowired
    ChartAssetRepositoryIntegrationTest(EntityManager entityManager, AssetReadService service) {
        this.entityManager = entityManager;
        this.service = service;
    }

    @Test
    @DisplayName("원화 거래지원 종목만 마켓 코드순으로 조회하고 중단·폐지 종목을 제외한다")
    void filterAndOrderChartAssets() {
        Asset ethereum = asset("ETH", "KRW");
        Asset bitcoin = asset("BTC", "KRW");
        Asset suspended = asset("XRP", "KRW");
        suspended.suspendTrading();
        Asset delisted = asset("ADA", "KRW");
        delisted.endTradingSupport(Instant.now());
        entityManager.persist(ethereum);
        entityManager.persist(bitcoin);
        entityManager.persist(suspended);
        entityManager.persist(delisted);
        entityManager.persist(asset("ETH", "BTC"));
        entityManager.flush();
        entityManager.clear();

        assertThat(service.findChartAssets()).extracting(Asset::getExchangeMarketCode)
                .containsExactly("KRW-BTC", "KRW-ETH");
    }

    private Asset asset(String base, String quote) {
        return new Asset(Exchange.UPBIT, quote + "-" + base, base, quote, base, base, null);
    }

    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = Asset.class)
    @EnableJpaRepositories(basePackageClasses = AssetRepository.class)
    @Import(AssetReadService.class)
    static class TestConfiguration {
    }
}
