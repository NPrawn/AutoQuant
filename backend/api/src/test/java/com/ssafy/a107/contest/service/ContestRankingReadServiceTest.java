package com.ssafy.a107.contest.service;

import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.contest.domain.ContestRankingSort;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.domain.MarketDatasetKind;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestEvaluationRepository;
import com.ssafy.a107.contest.repository.ContestRankingRow;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.MarketDatasetRepository;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/*
 * 랭킹 조회가 화면 조건을 어떤 쿼리 인수로 바꾸는지 본다. 쿼리 자체는 DB 가 있어야 돌아 로컬 확인으로 대신한다.
 */
class ContestRankingReadServiceTest {

    private static final UUID ROUND_ID = UUID.fromString("0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10");

    private final ContestRoundRepository contestRoundRepository = mock(ContestRoundRepository.class);
    private final ContestResultRepository contestResultRepository = mock(ContestResultRepository.class);
    private final StrategyRepository strategyRepository = mock(StrategyRepository.class);
    private final ContestResultReadService service = new ContestResultReadService(
            contestRoundRepository,
            mock(ContestEntryRepository.class),
            mock(ContestEvaluationRepository.class),
            contestResultRepository,
            mock(MarketDatasetRepository.class),
            mock(BacktestRunRepository.class),
            mock(BacktestResultRepository.class),
            mock(BacktestEquityRepository.class),
            strategyRepository
    );

    @DisplayName("전략 이름은 뿌리 기준 최신 판을 따라가고 판 번호는 제출한 판 그대로다")
    @Test
    void showTheLatestNameWithTheSubmittedVersion() {
        givenFinishedRound();
        UUID memberId = UUID.randomUUID();
        /* 뿌리 7번의 1판과 2판. 2판에서 이름을 바꿨고 대회에는 1판이 제출돼 있다. */
        Strategy submitted = mock(Strategy.class);
        given(submitted.getRootStrategyId()).willReturn(7L);
        given(submitted.getVersionNo()).willReturn(1);
        given(submitted.getName()).willReturn("옛 이름");
        Strategy latest = mock(Strategy.class);
        given(latest.getRootStrategyId()).willReturn(7L);
        given(latest.getVersionNo()).willReturn(2);
        given(latest.getName()).willReturn("새 이름");

        given(contestResultRepository.findRankings(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(row(7L, "옛 이름", 1, memberId))));
        given(strategyRepository.findAllByRootIn(any())).willReturn(List.of(submitted, latest));

        ContestResultReadService.ContestRankings rankings = service.getRankings(
                ROUND_ID, ContestRankingSort.TOTAL, null, null, PageRequest.of(0, 20));

        assertThat(rankings.page().getContent()).singleElement().satisfies(row -> {
            assertThat(row.strategyName()).isEqualTo("새 이름");
            assertThat(row.versionNo()).isEqualTo(1);
        });
    }

    private static ContestRankingRow row(long rootStrategyId, String name, int versionNo, UUID memberId) {
        BigDecimal score = new BigDecimal("50.000");
        return new ContestRankingRow(1, rootStrategyId, name, "참가자", true,
                score, score, score, score, score, score,
                new BigDecimal("0.53000000"), new BigDecimal("0.21000000"),
                rootStrategyId, versionNo, memberId);
    }

    @DisplayName("검색어는 소문자 부분 일치로 바꾸고 국면 필터는 해당 국면만 켠다")
    @Test
    void translateFilters() {
        givenFinishedRound();
        givenEmptyPage();

        service.getRankings(ROUND_ID, ContestRankingSort.STABILITY, MarketDatasetKind.BEAR, "  Momentum  ",
                PageRequest.of(1, 50));

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> bull = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> bear = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> sideways = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> byStability = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.BDDMockito.then(contestResultRepository).should().findRankings(
                eq(ROUND_ID), keyword.capture(), bull.capture(), bear.capture(), sideways.capture(),
                byStability.capture(), pageable.capture());

        assertThat(keyword.getValue()).isEqualTo("%momentum%");
        assertThat(bull.getValue()).isFalse();
        assertThat(bear.getValue()).isTrue();
        assertThat(sideways.getValue()).isFalse();
        assertThat(byStability.getValue()).isTrue();
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
    }

    @DisplayName("검색어가 비어 있으면 조건에서 뺀다")
    @Test
    void skipBlankQuery() {
        givenFinishedRound();
        givenEmptyPage();

        service.getRankings(ROUND_ID, ContestRankingSort.TOTAL, null, "   ", PageRequest.of(0, 20));

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> byStability = ArgumentCaptor.forClass(Boolean.class);
        org.mockito.BDDMockito.then(contestResultRepository).should().findRankings(
                eq(ROUND_ID), keyword.capture(), eq(false), eq(false), eq(false), byStability.capture(), any());

        assertThat(keyword.getValue()).isNull();
        assertThat(byStability.getValue()).isFalse();
    }

    @DisplayName("실격 수를 순위와 함께 돌려준다")
    @Test
    void reportDisqualifiedCount() {
        givenFinishedRound();
        Page<ContestRankingRow> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        given(contestResultRepository.findRankings(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), any())).willReturn(page);
        given(contestResultRepository.countDisqualifiedByRoundId(ROUND_ID)).willReturn(6L);

        ContestResultReadService.ContestRankings rankings =
                service.getRankings(ROUND_ID, ContestRankingSort.TOTAL, null, null, PageRequest.of(0, 20));

        assertThat(rankings.disqualifiedCount()).isEqualTo(6L);
        assertThat(rankings.page().getTotalElements()).isZero();
    }

    @DisplayName("마감 전에도 잠정 순위를 연다. 점수가 절대값이라 채점된 제출끼리 줄을 세운다")
    @Test
    void openRankingsBeforeTheDeadline() {
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(round()));
        givenEmptyPage();

        ContestResultReadService.ContestRankings rankings = service.getRankings(
                ROUND_ID, ContestRankingSort.TOTAL, null, null, PageRequest.of(0, 20));

        assertThat(rankings.page().getContent()).isEmpty();
    }

    @DisplayName("없는 회차는 찾을 수 없다고 답한다")
    @Test
    void rejectUnknownRound() {
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRankings(
                ROUND_ID, ContestRankingSort.TOTAL, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("회차를 찾을 수 없습니다");
    }

    private void givenFinishedRound() {
        ContestRound round = round();
        round.startScoring(Instant.parse("2026-10-16T00:00:00Z"));
        round.finish(Instant.parse("2026-10-16T00:10:00Z"));
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(round));
    }

    private void givenEmptyPage() {
        given(contestResultRepository.findRankings(any(), any(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), any())).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    private ContestRound round() {
        return new ContestRound(
                "1회차 모의 대회",
                Instant.parse("2026-09-16T00:00:00Z"),
                Instant.parse("2026-10-16T00:00:00Z"),
                30,
                13
        );
    }
}
