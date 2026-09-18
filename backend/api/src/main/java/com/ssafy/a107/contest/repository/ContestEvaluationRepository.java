package com.ssafy.a107.contest.repository;

import com.ssafy.a107.contest.domain.ContestEvaluation;
import com.ssafy.a107.contest.domain.ContestEvaluationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ContestEvaluationRepository extends JpaRepository<ContestEvaluation, ContestEvaluationId> {

    List<ContestEvaluation> findAllByIdEntryIdIn(List<UUID> entryIds);

    /* 교체 제출이면 이전 평가를 버린다. 백테스트 실행 기록은 남는다. */
    void deleteAllByIdEntryId(UUID entryId);

    @Query("""
            select  evaluation
            from ContestEvaluation evaluation
            where evaluation.id.entryId in (
                select entry.entryId from ContestEntry entry where entry.round.roundId = :roundId
            )
            """)
    List<ContestEvaluation> findAllByRoundId(@Param("roundId") UUID roundId);
}
