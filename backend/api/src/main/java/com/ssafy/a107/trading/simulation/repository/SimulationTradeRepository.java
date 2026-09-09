package com.ssafy.a107.trading.simulation.repository;

import com.ssafy.a107.trading.simulation.domain.SimulationTrade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SimulationTradeRepository extends JpaRepository<SimulationTrade, UUID> {

    @EntityGraph(attributePaths = "asset")
    Page<SimulationTrade> findAllBySimulationRunSimulationIdOrderByFilledAtAscTradeIdAsc(UUID simulationId, Pageable pageable);
}
