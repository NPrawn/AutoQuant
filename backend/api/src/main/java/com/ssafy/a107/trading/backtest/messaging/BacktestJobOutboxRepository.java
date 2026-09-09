package com.ssafy.a107.trading.backtest.messaging;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BacktestJobOutboxRepository
        extends JpaRepository<BacktestJobOutbox, UUID> {

    List<BacktestJobOutbox> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select outbox
            from BacktestJobOutbox outbox
            where outbox.jobId = :jobId
            """)
    Optional<BacktestJobOutbox> findByJobIdForUpdate(
            @Param("jobId") UUID jobId
    );
}
