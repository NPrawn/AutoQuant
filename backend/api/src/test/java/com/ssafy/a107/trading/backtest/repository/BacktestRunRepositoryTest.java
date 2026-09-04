package com.ssafy.a107.trading.backtest.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BacktestRunRepositoryTest {

    @DisplayName("백테스트 실행은 백테스트 ID와 회원 ID로 함께 조회한다")
    @Test
    void findBacktestRunByIdAndMemberId() throws NoSuchMethodException {
        Method method = BacktestRunRepository.class.getMethod(
                "findByBacktestIdAndMemberMemberId",
                UUID.class,
                UUID.class
        );

        assertThat(method.getReturnType()).isEqualTo(Optional.class);
    }
}
