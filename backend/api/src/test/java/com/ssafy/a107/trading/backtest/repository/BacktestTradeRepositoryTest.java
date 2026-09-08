package com.ssafy.a107.trading.backtest.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestTradeRepositoryTest {

    @DisplayName("체결 목록은 자산 정보를 함께 조회한다")
    @Test
    void fetchAssetWithBacktestTrades() throws NoSuchMethodException {
        Method method = BacktestTradeRepository.class.getMethod(
                "findAllByBacktestRunBacktestIdOrderByFilledAtAscTradeIdAsc",
                UUID.class,
                Pageable.class
        );

        assertThat(method.getReturnType()).isEqualTo(Page.class);
        assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
                .containsExactly("asset");
    }
}
