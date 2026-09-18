package com.ssafy.a107.trading.backtest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.trading.backtest.domain.BacktestEquity;
import com.ssafy.a107.trading.backtest.domain.BacktestResult;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import com.ssafy.a107.trading.backtest.domain.BacktestTrade;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestTradeRepository;
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
    private BacktestResultRepository backtestResultRepository;

    @Mock
    private BacktestTradeRepository backtestTradeRepository;

    @Mock
    private BacktestEquityRepository backtestEquityRepository;

    @Mock
    private Pageable pageable;

    @Test
    @DisplayName("회원별 백테스트 실행 목록을 조회한다")
    void getBacktestRuns() {
        BacktestRun run = org.mockito.Mockito.mock(BacktestRun.class);
        Page<BacktestRun> expected = new PageImpl<>(List.of(run));
        given(backtestRunRepository.findAllByMemberMemberIdAndContestDatasetIdIsNullOrderByQueuedAtDesc(MEMBER_ID, pageable))
                .willReturn(expected);

        Page<BacktestRun> actual = service()
                .getBacktestRuns(MEMBER_ID, pageable);

        assertThat(actual).isSameAs(expected);
        then(backtestRunRepository).should()
                .findAllByMemberMemberIdAndContestDatasetIdIsNullOrderByQueuedAtDesc(MEMBER_ID, pageable);
    }

    @Test
    @DisplayName("회원 소유가 아닌 백테스트 실행은 단건 조회할 수 없다")
    void rejectBacktestRunOutsideMemberOwnership() {
        given(backtestRunRepository.findByBacktestIdAndMemberMemberIdAndContestDatasetIdIsNull(BACKTEST_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service()
                .getBacktestRun(MEMBER_ID, BACKTEST_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.BACKTEST_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("대회 백테스트는 연습 화면에서 찾을 수 없다. 체결과 자산 추이로 채점용 데이터가 드러난다")
    void hideContestBacktestRuns() {
        /* 조회가 대회 실행을 빼는 메서드를 쓰므로 대회 실행이면 빈 값이 온다. */
        given(backtestRunRepository.findByBacktestIdAndMemberMemberIdAndContestDatasetIdIsNull(BACKTEST_ID, MEMBER_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service().getBacktestResult(MEMBER_ID, BACKTEST_ID))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.BACKTEST_NOT_FOUND)
                );
        then(backtestResultRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소유한 백테스트 실행의 최종 결과를 조회한다")
    void getBacktestResult() {
        BacktestRun run = org.mockito.Mockito.mock(BacktestRun.class);
        BacktestResult result = org.mockito.Mockito.mock(BacktestResult.class);
        given(backtestRunRepository.findByBacktestIdAndMemberMemberIdAndContestDatasetIdIsNull(BACKTEST_ID, MEMBER_ID))
                .willReturn(Optional.of(run));
        given(backtestResultRepository.findById(BACKTEST_ID))
                .willReturn(Optional.of(result));

        BacktestResult actual = service().getBacktestResult(
                MEMBER_ID,
                BACKTEST_ID
        );

        assertThat(actual).isSameAs(result);
        then(backtestTradeRepository).shouldHaveNoInteractions();
        then(backtestEquityRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("소유한 백테스트 실행의 체결과 Equity 이력을 시간순으로 조회한다")
    void getBacktestTradesAndEquities() {
        BacktestRun run = org.mockito.Mockito.mock(BacktestRun.class);
        Page<BacktestTrade> trades = new PageImpl<>(List.of(
                org.mockito.Mockito.mock(BacktestTrade.class)
        ));
        Page<BacktestEquity> equities = new PageImpl<>(List.of(
                org.mockito.Mockito.mock(BacktestEquity.class)
        ));
        given(backtestRunRepository.findByBacktestIdAndMemberMemberIdAndContestDatasetIdIsNull(BACKTEST_ID, MEMBER_ID))
                .willReturn(Optional.of(run));
        given(backtestTradeRepository
                .findAllByBacktestRunBacktestIdOrderByFilledAtAscTradeIdAsc(
                        BACKTEST_ID,
                        pageable
                ))
                .willReturn(trades);
        given(backtestEquityRepository
                .findAllByBacktestRunBacktestIdOrderByIdValuationAtAsc(
                        BACKTEST_ID,
                        pageable
                ))
                .willReturn(equities);

        assertThat(service().getBacktestTrades(
                MEMBER_ID,
                BACKTEST_ID,
                pageable
        )).isSameAs(trades);
        assertThat(service().getBacktestEquities(
                MEMBER_ID,
                BACKTEST_ID,
                pageable
        )).isSameAs(equities);

        then(backtestTradeRepository).should()
                .findAllByBacktestRunBacktestIdOrderByFilledAtAscTradeIdAsc(
                        BACKTEST_ID,
                        pageable
                );
        then(backtestEquityRepository).should()
                .findAllByBacktestRunBacktestIdOrderByIdValuationAtAsc(
                        BACKTEST_ID,
                        pageable
                );
    }

    private BacktestRunReadService service() {
        return new BacktestRunReadService(
                backtestRunRepository,
                backtestResultRepository,
                backtestTradeRepository,
                backtestEquityRepository
        );
    }
}
