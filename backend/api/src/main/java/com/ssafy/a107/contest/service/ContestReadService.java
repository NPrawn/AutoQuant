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
import com.ssafy.a107.strategy.domain.Strategy;
import com.ssafy.a107.strategy.repository.StrategyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ContestReadService {

    private final ContestRoundRepository contestRoundRepository;
    private final ContestEntryRepository contestEntryRepository;
    private final ContestResultRepository contestResultRepository;
    private final StrategyRepository strategyRepository;

    public ContestReadService(
            ContestRoundRepository contestRoundRepository,
            ContestEntryRepository contestEntryRepository,
            ContestResultRepository contestResultRepository,
            StrategyRepository strategyRepository
    ) {
        this.contestRoundRepository = contestRoundRepository;
        this.contestEntryRepository = contestEntryRepository;
        this.contestResultRepository = contestResultRepository;
        this.strategyRepository = strategyRepository;
    }

    /*
     * 목록은 회차가 여럿이라 회차마다 질의하지 않는다. 참가 수, 내 제출, 내 결과, 회차 1위를 각각
     * 한 번씩 읽어 맞춘다.
     */
    public List<ContestRoundSummary> getRounds(UUID memberId) {
        Instant now = Instant.now();

        /* 제출마다 행이 쌓이므로 회차마다 마지막 제출만 남긴다. 목록은 한 회차에 한 줄이다. */
        Map<UUID, ContestEntry> myEntries = new HashMap<>();
        for (ContestEntry entry : contestEntryRepository.findAllByMemberMemberIdOrderBySubmittedAtDesc(memberId)) {
            myEntries.putIfAbsent(entry.getRound().getRoundId(), entry);
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

    /*
     * 내 제출 전체를 최신순으로 준다. 제출마다 행이 쌓이므로 같은 회차가 여러 줄 나온다.
     *
     * 회차 수와 제출 수가 적어 페이징을 두지 않는다. 늘면 그때 붙인다.
     */
    public List<ContestMyEntry> getMyEntries(UUID memberId) {
        List<ContestEntry> entries = contestEntryRepository.findAllByMemberMemberIdOrderBySubmittedAtDesc(memberId);
        if (entries.isEmpty()) {
            return List.of();
        }

        Map<UUID, ContestResult> results = new HashMap<>();
        Set<UUID> entryIds = entries.stream().map(ContestEntry::getEntryId).collect(Collectors.toSet());
        for (ContestResult result : contestResultRepository.findAllByEntryIdIn(entryIds)) {
            results.put(result.getEntryId(), result);
        }

        Map<Long, String> latestNames = latestNames(entries);
        Instant now = Instant.now();
        List<ContestMyEntry> rows = new ArrayList<>();
        for (ContestEntry entry : entries) {
            ContestResult result = results.get(entry.getEntryId());
            Strategy strategy = entry.getStrategy();
            rows.add(new ContestMyEntry(
                    entry.getRound().getRoundId(),
                    entry.getRound().getTitle(),
                    entry.getRound().displayStatus(now),
                    strategy.getStrategyId(),
                    latestNames.getOrDefault(strategy.getRootStrategyId(), strategy.getName()),
                    strategy.getVersionNo(),
                    entry.getSubmittedAt(),
                    result == null ? null : result.getTotalScore(),
                    result == null ? null : result.getRanking(),
                    result == null ? null : result.getDisqualificationReason()
            ));
        }
        return List.copyOf(rows);
    }

    /* 전략 이름은 뿌리 기준 최신 판을 따라간다. 순위표와 같은 규칙이다. */
    private Map<Long, String> latestNames(List<ContestEntry> entries) {
        Set<Long> rootIds = entries.stream()
                .map(entry -> entry.getStrategy().getRootStrategyId())
                .collect(Collectors.toSet());

        Map<Long, Strategy> latest = new LinkedHashMap<>();
        for (Strategy strategy : strategyRepository.findAllByRootIn(rootIds)) {
            latest.merge(strategy.getRootStrategyId(), strategy,
                    (kept, other) -> kept.getVersionNo() >= other.getVersionNo() ? kept : other);
        }

        Map<Long, String> names = new LinkedHashMap<>();
        latest.forEach((rootId, strategy) -> names.put(rootId, strategy.getName()));
        return names;
    }

    public ContestRoundDetail getRound(UUID memberId, UUID roundId) {
        ContestRound round = contestRoundRepository.findById(roundId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.CONTEST_ROUND_NOT_FOUND));

        return new ContestRoundDetail(
                round,
                round.displayStatus(Instant.now()),
                contestEntryRepository.countByRoundRoundId(roundId),
                contestEntryRepository.findFirstByRoundRoundIdAndMemberMemberIdOrderBySubmittedAtDesc(roundId, memberId)
                        .orElse(null),
                ContestFactorPeriodPolicy.caps(round.getWarmupWeeks())
        );
    }
}
