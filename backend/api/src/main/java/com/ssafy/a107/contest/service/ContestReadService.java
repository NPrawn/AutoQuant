package com.ssafy.a107.contest.service;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.contest.domain.ContestEntry;
import com.ssafy.a107.contest.domain.ContestFactorPeriodPolicy;
import com.ssafy.a107.contest.domain.ContestRound;
import com.ssafy.a107.contest.repository.ContestEntryRepository;
import com.ssafy.a107.contest.repository.ContestRoundRepository;
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

    public ContestReadService(
            ContestRoundRepository contestRoundRepository,
            ContestEntryRepository contestEntryRepository
    ) {
        this.contestRoundRepository = contestRoundRepository;
        this.contestEntryRepository = contestEntryRepository;
    }

    public List<ContestRoundSummary> getRounds(UUID memberId) {
        Instant now = Instant.now();
        Map<UUID, ContestEntry> myEntries = new HashMap<>();
        for (ContestEntry entry : contestEntryRepository.findAllByMemberMemberId(memberId)) {
            myEntries.put(entry.getRound().getRoundId(), entry);
        }

        return contestRoundRepository.findAllByOrderBySubmissionStartsAtDesc().stream()
                .map(round -> new ContestRoundSummary(
                        round,
                        round.displayStatus(now),
                        contestEntryRepository.countByRoundRoundId(round.getRoundId()),
                        myEntries.get(round.getRoundId())
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
