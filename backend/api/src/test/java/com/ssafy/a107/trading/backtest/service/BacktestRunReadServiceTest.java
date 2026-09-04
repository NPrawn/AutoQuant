package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BacktestRunReadServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID BACKTEST_ID = UUID.fromString("e4b4e9e0-8d17-4a47-98d4-4d9f06d97b2b");

    @Mock
    private BacktestRunRepository backtestRunRepository;

    @Mock
    private Pageable pageable;

    @Test
    @DisplayName("회원별 백테스트 실행 목록을 조회한다")
    void getBacktestRuns() {
        BacktestRun run = org.mockito.Mockito.mock(BacktestRun.class);
        Page<BacktestRun> expected = new PageImpl<>(List.of(run));
        given(backtestRunRepository.findAllByMemberMemberIdOrderByQueuedAtDesc(MEMBER_ID, pageable))
                .willReturn(expected);

        Page<BacktestRun> actual = new BacktestRunReadService(backtestRunRepository)
                .getBacktestRuns(MEMBER_ID, pageable);

        assertThat(actual).isSameAs(expected);
        then(backtestRunRepository).should()
                .findAllByMemberMemberIdOrderByQueuedAtDesc(MEMBER_ID, pageable);
    }

    @Test
    @DisplayName("회원 소유가 아닌 백테스트 실행은 단건 조회할 수 없다")
    void rejectBacktestRunOutsideMemberOwnership() {
        given(backtestRunRepository.findByBacktestIdAndMemberMemberId(BACKTEST_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> new BacktestRunReadService(backtestRunRepository)
                .getBacktestRun(MEMBER_ID, BACKTEST_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.BACKTEST_NOT_FOUND)
                );
    }
}
