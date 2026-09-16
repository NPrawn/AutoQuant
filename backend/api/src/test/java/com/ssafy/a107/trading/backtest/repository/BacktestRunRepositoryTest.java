package com.ssafy.a107.trading.backtest.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

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
        assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
                .containsExactly("strategy");
    }

    @DisplayName("회원별 백테스트 실행 목록을 최신 대기 시각순으로 페이지 조회한다")
    @Test
    void findBacktestRunsByMemberId() throws NoSuchMethodException {
        Method method = BacktestRunRepository.class.getMethod(
                "findAllByMemberMemberIdAndContestDatasetIdIsNullOrderByQueuedAtDesc",
                UUID.class,
                Pageable.class
        );

        assertThat(method.getReturnType()).isEqualTo(Page.class);
        assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
                .containsExactly("strategy");
    }
}
