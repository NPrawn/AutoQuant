package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundEntryCount;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.ContestRoundTopResult;
import com.ssafy.a107.member.domain.LoginProvider;
import com.ssafy.a107.member.domain.Member;
import com.ssafy.a107.strategy.domain.Strategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/*
 * 대회 목록은 회차가 여럿이라 회차마다 질의하지 않는다. 참가 수, 내 제출, 내 결과, 1위를 한 번씩 읽어 맞춘다.
 */
class ContestReadServiceTest {

    private static final UUID MEMBER_ID = UUID.fromString("b7ced58c-00e9-4617-9658-4f576ea2f6fe");
    private static final UUID FIRST_ROUND = UUID.fromString("0f5f4f2a-9c4e-4b7a-8a3f-1c0e5a7b2d10");
    private static final UUID SECOND_ROUND = UUID.fromString("1a2b3c4d-9c4e-4b7a-8a3f-1c0e5a7b2d10");
    private static final UUID MY_ENTRY = UUID.fromString("6deb875a-2007-4486-8441-493b7eb307fb");

    private final ContestRoundRepository contestRoundRepository = mock(ContestRoundRepository.class);
    private final ContestEntryRepository contestEntryRepository = mock(ContestEntryRepository.class);
    private final ContestResultRepository contestResultRepository = mock(ContestResultRepository.class);
    private final ContestReadService service =
            new ContestReadService(contestRoundRepository, contestEntryRepository, contestResultRepository);

    @DisplayName("회차별로 참가 수와 내 순위, 1위를 맞춰 담는다")
    @Test
    void assembleRankingsPerRound() {
        ContestRound first = round(FIRST_ROUND, "1회차");
        ContestRound second = round(SECOND_ROUND, "2회차");
        given(contestRoundRepository.findAllByOrderBySubmissionStartsAtDesc()).willReturn(List.of(second, first));
        given(contestEntryRepository.findAllByMemberMemberId(MEMBER_ID)).willReturn(List.of(entry(first)));
        given(contestEntryRepository.countByRound()).willReturn(List.of(new ContestRoundEntryCount(FIRST_ROUND, 12)));
        given(contestResultRepository.findAllByEntryIdIn(any())).willReturn(List.of(scored(MY_ENTRY, 2)));
        given(contestResultRepository.findTopResults()).willReturn(List.of(
                new ContestRoundTopResult(FIRST_ROUND, "변동성 눌림 스윙", "가온", new BigDecimal("55.180"),
                        UUID.randomUUID())));

        List<ContestRoundSummary> summaries = service.getRounds(MEMBER_ID);

        ContestRoundSummary secondSummary = summaries.get(0);
        assertThat(secondSummary.entryCount()).isZero();
        assertThat(secondSummary.myEntry()).isNull();
        assertThat(secondSummary.myResult()).isNull();
        assertThat(secondSummary.top()).isNull();

        ContestRoundSummary firstSummary = summaries.get(1);
        assertThat(firstSummary.entryCount()).isEqualTo(12);
        assertThat(firstSummary.myResult().getRanking()).isEqualTo(2);
        assertThat(firstSummary.top().strategyName()).isEqualTo("변동성 눌림 스윙");
    }

    @DisplayName("회차 수와 무관하게 각 조회를 한 번씩만 한다")
    @Test
    void queryOncePerKind() {
        ContestRound first = round(FIRST_ROUND, "1회차");
        ContestRound second = round(SECOND_ROUND, "2회차");
        given(contestRoundRepository.findAllByOrderBySubmissionStartsAtDesc()).willReturn(List.of(second, first));
        given(contestEntryRepository.findAllByMemberMemberId(MEMBER_ID)).willReturn(List.of(entry(first)));
        given(contestEntryRepository.countByRound()).willReturn(List.of());
        given(contestResultRepository.findAllByEntryIdIn(any())).willReturn(List.of());
        given(contestResultRepository.findTopResults()).willReturn(List.of());

        service.getRounds(MEMBER_ID);

        then(contestEntryRepository).should(times(1)).countByRound();
        then(contestEntryRepository).should(never()).countByRoundRoundId(any());
        then(contestResultRepository).should(times(1)).findAllByEntryIdIn(any());
        then(contestResultRepository).should(times(1)).findTopResults();
    }

    @DisplayName("제출한 회차가 없으면 내 결과를 조회하지 않는다")
    @Test
    void skipMyResultsWithoutEntries() {
        given(contestRoundRepository.findAllByOrderBySubmissionStartsAtDesc())
                .willReturn(List.of(round(FIRST_ROUND, "1회차")));
        given(contestEntryRepository.findAllByMemberMemberId(MEMBER_ID)).willReturn(List.of());
        given(contestEntryRepository.countByRound()).willReturn(List.of());
        given(contestResultRepository.findTopResults()).willReturn(List.of());

        List<ContestRoundSummary> summaries = service.getRounds(MEMBER_ID);

        assertThat(summaries).singleElement().satisfies(summary -> assertThat(summary.myResult()).isNull());
        then(contestResultRepository).should(never()).findAllByEntryIdIn(any());
    }

    private static ContestRound round(UUID roundId, String title) {
        ContestRound round = new ContestRound(
                title,
                Instant.parse("2026-09-16T00:00:00Z"),
                Instant.parse("2026-09-20T15:00:00Z"),
                30,
                13
        );
        ReflectionTestUtils.setField(round, "roundId", roundId);
        return round;
    }

    private static ContestEntry entry(ContestRound round) {
        Member member = new Member(LoginProvider.SSAFY, "contest-member-107", "contest@example.com", "대회 참가자");
        ContestEntry entry = new ContestEntry(round, member, mock(Strategy.class), Instant.parse("2026-09-17T00:00:00Z"));
        ReflectionTestUtils.setField(entry, "entryId", MY_ENTRY);
        return entry;
    }

    private static ContestResult scored(UUID entryId, int ranking) {
        return ContestResult.scored(
                entryId,
                new BigDecimal("78.667"),
                new BigDecimal("18.800"),
                new BigDecimal("17.000"),
                new BigDecimal("18.200"),
                new BigDecimal("17.600"),
                new BigDecimal("48.133"),
                new BigDecimal("0.53000000"),
                new BigDecimal("0.21000000"),
                ranking
        );
    }
}
