package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestPhase;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestEvaluationRepository;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.MarketDatasetRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestEquityRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestResultRepository;
import com.ssafy.a107.trading.backtest.repository.BacktestRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ContestScoringServiceTest {

    private static final UUID ROUND_ID = UUID.fromString("0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10");
    private static final Instant SUBMISSION_STARTS_AT = Instant.parse("2026-09-16T15:00:00Z");
    private static final Instant SUBMISSION_ENDS_AT = Instant.parse("2026-09-20T15:00:00Z");
    private static final Instant NOW = Instant.parse("2026-09-21T00:00:00Z");

    private final ContestRoundRepository contestRoundRepository = mock(ContestRoundRepository.class);
    private final ContestEntryRepository contestEntryRepository = mock(ContestEntryRepository.class);
    private final MarketDatasetRepository marketDatasetRepository = mock(MarketDatasetRepository.class);
    private final ContestEvaluationRepository contestEvaluationRepository = mock(ContestEvaluationRepository.class);
    private final ContestResultRepository contestResultRepository = mock(ContestResultRepository.class);
    private final ContestEvaluationService contestEvaluationService = mock(ContestEvaluationService.class);
    private final BacktestRunRepository backtestRunRepository = mock(BacktestRunRepository.class);
    private final BacktestResultRepository backtestResultRepository = mock(BacktestResultRepository.class);
    private final BacktestEquityRepository backtestEquityRepository = mock(BacktestEquityRepository.class);
    private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);

    private final ContestScoringService service = new ContestScoringService(
            contestRoundRepository,
            contestEntryRepository,
            marketDatasetRepository,
            contestEvaluationRepository,
            contestResultRepository,
            contestEvaluationService,
            backtestRunRepository,
            backtestResultRepository,
            backtestEquityRepository,
            applicationEventPublisher
    );

    /*
     * 채점은 회차 결과를 통째로 지우며 영속성 컨텍스트를 비운다. 그래서 채점 전에 읽어 둔 회차는 준영속이 되고,
     * 그 객체를 종료시키면 커밋되지 않아 회차가 SCORING 에 영영 머무른다. 채점 뒤에 다시 읽는지 확인한다.
     */
    @DisplayName("채점 뒤 회차를 다시 읽어 종료시킨다")
    @Test
    void reloadRoundBeforeFinishing() {
        ContestRound beforeScoring = scoringRound();
        ContestRound afterScoring = scoringRound();
        given(contestRoundRepository.findById(ROUND_ID))
                .willReturn(Optional.of(beforeScoring), Optional.of(afterScoring));
        given(contestEvaluationRepository.findAllByRoundId(ROUND_ID)).willReturn(List.of());
        given(marketDatasetRepository.findAllByRoundRoundIdOrderByKindAsc(ROUND_ID)).willReturn(List.of());
        given(contestResultRepository.findAllByRoundId(ROUND_ID)).willReturn(List.of());

        boolean finished = service.finishScoring(ROUND_ID, NOW);

        assertThat(finished).isTrue();
        assertThat(afterScoring.getPhase()).isEqualTo(ContestPhase.FINISHED);
        assertThat(afterScoring.getFinishedAt()).isEqualTo(NOW);
    }

    @DisplayName("채점 중이 아닌 회차는 종료시키지 않는다")
    @Test
    void skipRoundThatIsNotScoring() {
        given(contestRoundRepository.findById(ROUND_ID)).willReturn(Optional.of(readyRound()));

        assertThat(service.finishScoring(ROUND_ID, NOW)).isFalse();
    }

    private ContestRound readyRound() {
        return new ContestRound("1회차 모의 대회", SUBMISSION_STARTS_AT, SUBMISSION_ENDS_AT, 5, 5, 13);
    }

    private ContestRound scoringRound() {
        ContestRound round = readyRound();
        round.startScoring(SUBMISSION_ENDS_AT);
        return round;
    }
}
