package com.ssafy.a107.trading.simulation.repository;

import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.trading.simulation.domain.SimulationRun;
import com.ssafy.a107.trading.simulation.domain.SimulationRunStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SimulationRunRepository extends JpaRepository<SimulationRun, UUID> {

    @EntityGraph(attributePaths = "strategy")
    Optional<SimulationRun> findBySimulationIdAndMemberMemberId(UUID simulationId, UUID memberId);

    @EntityGraph(attributePaths = "strategy")
    Page<SimulationRun> findAllByMemberMemberIdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "strategy")
    @Query("select simulationRun from SimulationRun simulationRun where simulationRun.simulationId = :simulationId")
    Optional<SimulationRun> findBySimulationIdForUpdate(@Param("simulationId") UUID simulationId);

    @EntityGraph(attributePaths = "strategy")
    Page<SimulationRun> findAllByStatusAndTimeframe(
            SimulationRunStatus status,
            Timeframe timeframe,
            Pageable pageable
    );

    @Query("select simulationRun.simulationId from SimulationRun simulationRun "
            + "where simulationRun.status = :status and simulationRun.timeframe = :timeframe "
            + "order by simulationRun.simulationId")
    java.util.List<UUID> findSimulationIdsByStatusAndTimeframe(
            @Param("status") SimulationRunStatus status,
            @Param("timeframe") Timeframe timeframe,
            Pageable pageable
    );
}
