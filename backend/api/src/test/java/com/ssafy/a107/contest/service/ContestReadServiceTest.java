package com.ssafy.a107.contest.service;

import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundEntryCount;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.ContestRoundTopResult;
import com.ssafy.a107.strategy.repository.StrategyRepository;
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
    private final StrategyRepository strategyRepository = mock(StrategyRepository.class);
    private final ContestReadService service = new ContestReadService(
            contestRoundRepository, contestEntryRepository, contestResultRepository, strategyRepository);

    @DisplayName("회차별로 참가 수와 내 순위, 1위를 맞춰 담는다")
    @Test
    void assembleRankingsPerRound() {
        ContestRound first = round(FIRST_ROUND, "1회차");
        ContestRound second = round(SECOND_ROUND, "2회차");
        given(contestRoundRepository.findAllByOrderBySubmissionStartsAtDesc()).willReturn(List.of(second, first));
        given(contestEntryRepository.findAllByMemberMemberIdOrderBySubmittedAtDesc(MEMBER_ID)).willReturn(List.of(entry(first)));
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
        given(contestEntryRepository.findAllByMemberMemberIdOrderBySubmittedAtDesc(MEMBER_ID)).willReturn(List.of(entry(first)));
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
        given(contestEntryRepository.findAllByMemberMemberIdOrderBySubmittedAtDesc(MEMBER_ID)).willReturn(List.of());
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

    @DisplayName("내 제출 목록은 제출마다 한 줄이고 같은 회차가 여러 번 나온다")
    @Test
    void listEveryEntryOfMine() {
        ContestRound round = round(FIRST_ROUND, "1회차");
        ContestEntry later = entry(round, 9L, "옛 이름", 1, "2026-09-18T00:00:00Z");
        ContestEntry earlier = entry(round, 9L, "옛 이름", 1, "2026-09-17T00:00:00Z");
        given(contestEntryRepository.findAllByMemberMemberIdOrderBySubmittedAtDesc(MEMBER_ID))
                .willReturn(List.of(later, earlier));
        given(contestResultRepository.findAllByEntryIdIn(any())).willReturn(List.of());
        given(strategyRepository.findAllByRootIn(any())).willReturn(List.of());

        List<ContestMyEntry> entries = service.getMyEntries(MEMBER_ID);

        assertThat(entries).hasSize(2);
        assertThat(entries).extracting(ContestMyEntry::roundTitle).containsOnly("1회차");
        /* 채점 전이라 점수와 순위가 비어 있다. 화면이 채점 중으로 읽는다. */
        assertThat(entries).extracting(ContestMyEntry::totalScore).containsOnlyNulls();
        assertThat(entries).extracting(ContestMyEntry::ranking).containsOnlyNulls();
    }

    @DisplayName("내 제출 목록의 전략 이름은 최신 판을 따라가고 판 번호는 제출한 판 그대로다")
    @Test
    void followTheLatestNameInMyEntries() {
        ContestEntry submitted = entry(round(FIRST_ROUND, "1회차"), 9L, "옛 이름", 1, "2026-09-17T00:00:00Z");
        Strategy latest = mock(Strategy.class);
        given(latest.getRootStrategyId()).willReturn(9L);
        given(latest.getVersionNo()).willReturn(2);
        given(latest.getName()).willReturn("새 이름");
        given(contestEntryRepository.findAllByMemberMemberIdOrderBySubmittedAtDesc(MEMBER_ID))
                .willReturn(List.of(submitted));
        given(contestResultRepository.findAllByEntryIdIn(any())).willReturn(List.of());
        given(strategyRepository.findAllByRootIn(any())).willReturn(List.of(latest));

        List<ContestMyEntry> entries = service.getMyEntries(MEMBER_ID);

        assertThat(entries).singleElement().satisfies(row -> {
            assertThat(row.strategyName()).isEqualTo("새 이름");
            assertThat(row.versionNo()).isEqualTo(1);
        });
    }

    private static ContestEntry entry(ContestRound round, long rootId, String name, int versionNo, String at) {
        Member member = new Member(LoginProvider.SSAFY, "contest-member-107", "contest@example.com", "대회 참가자");
        Strategy strategy = mock(Strategy.class);
        given(strategy.getStrategyId()).willReturn(rootId);
        given(strategy.getRootStrategyId()).willReturn(rootId);
        given(strategy.getName()).willReturn(name);
        given(strategy.getVersionNo()).willReturn(versionNo);
        ContestEntry entry = new ContestEntry(round, member, strategy, Instant.parse(at));
        ReflectionTestUtils.setField(entry, "entryId", UUID.randomUUID());
        return entry;
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
