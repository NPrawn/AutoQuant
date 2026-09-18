package com.ssafy.a107.contest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestFactorPeriodPolicy;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.domain.ContestResult;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestResultRepository;
import com.ssafy.a107.contest.repository.ContestRoundEntryCount;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
import com.ssafy.a107.contest.repository.ContestRoundTopResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ContestReadService {

    private final ContestRoundRepository contestRoundRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestResultRepository contestResultRepository;

    public ContestReadService(
            ContestRoundRepository contestRoundRepository,
            ContestEntryRepository contestEntryRepository,
            ContestResultRepository contestResultRepository
    ) {
        this.contestRoundRepository = contestRoundRepository;
        this.contestEntryRepository = contestEntryRepository;
        this.contestResultRepository = contestResultRepository;
    }

    /*
     * 목록은 회차가 여럿이라 회차마다 질의하지 않는다. 참가 수, 내 제출, 내 결과, 회차 1위를 각각
     * 한 번씩 읽어 맞춘다.
     */
    public List<ContestRoundSummary> getRounds(UUID memberId) {
        Instant now = Instant.now();

        Map<UUID, ContestEntry> myEntries = new HashMap<>();
        for (ContestEntry entry : contestEntryRepository.findAllByMemberMemberId(memberId)) {
            myEntries.put(entry.getRound().getRoundId(), entry);
        }

        Map<UUID, ContestResult> myResults = new HashMap<>();
        if (!myEntries.isEmpty()) {
            Map<UUID, UUID> roundIdByEntryId = new HashMap<>();
            myEntries.forEach((roundId, entry) -> roundIdByEntryId.put(entry.getEntryId(), roundId));
            for (ContestResult result : contestResultRepository.findAllByEntryIdIn(roundIdByEntryId.keySet())) {
                myResults.put(roundIdByEntryId.get(result.getEntryId()), result);
            }
        }

        Map<UUID, Long> entryCounts = new HashMap<>();
        for (ContestRoundEntryCount count : contestEntryRepository.countByRound()) {
            entryCounts.put(count.roundId(), count.entryCount());
        }

        Map<UUID, ContestRoundTopResult> tops = new HashMap<>();
        for (ContestRoundTopResult top : contestResultRepository.findTopResults()) {
            tops.putIfAbsent(top.roundId(), top);
        }

        return contestRoundRepository.findAllByOrderBySubmissionStartsAtDesc().stream()
                .map(round -> new ContestRoundSummary(
                        round,
                        round.displayStatus(now),
                        entryCounts.getOrDefault(round.getRoundId(), 0L),
                        myEntries.get(round.getRoundId()),
                        myResults.get(round.getRoundId()),
                        tops.get(round.getRoundId())
                ))
                .toList();
    }

    public ContestRoundDetail getRound(UUID memberId, UUID roundId) {
        ContestRound round = contestRoundRepository.findById(roundId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CONTEST_ROUND_NOT_FOUND));

        return new ContestRoundDetail(
                round,
                round.displayStatus(Instant.now()),
                contestEntryRepository.countByRoundRoundId(roundId),
                contestEntryRepository.findByRoundRoundIdAndMemberMemberId(roundId, memberId).orElse(null),
                ContestFactorPeriodPolicy.caps(round.getWarmupWeeks())
        );
    }
}
