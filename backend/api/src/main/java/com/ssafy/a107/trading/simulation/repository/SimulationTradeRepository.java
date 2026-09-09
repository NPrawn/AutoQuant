package com.ssafy.a107.trading.simulation.repository;

import com.ssafy.a107.trading.simulation.domain.SimulationTrade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.math.BigDecimal;

public interface SimulationTradeRepository extends JpaRepository<SimulationTrade, UUID> {

    @EntityGraph(attributePaths = "asset")
    Page<SimulationTrade> findAllBySimulationRunSimulationIdOrderByFilledAtAscTradeIdAsc(UUID simulationId, Pageable pageable);

    long countBySimulationRunSimulationId(UUID simulationId);

    @org.springframework.data.jpa.repository.Query("select coalesce(sum(trade.feeAmount), 0) from SimulationTrade trade where trade.simulationRun.simulationId = :simulationId")
    BigDecimal sumFeeAmountBySimulationId(@org.springframework.data.repository.query.Param("simulationId") UUID simulationId);
}
