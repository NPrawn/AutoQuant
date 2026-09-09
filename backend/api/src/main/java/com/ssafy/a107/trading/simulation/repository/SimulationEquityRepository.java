package com.ssafy.a107.trading.simulation.repository;

import com.ssafy.a107.trading.simulation.domain.SimulationEquity;
import com.ssafy.a107.trading.simulation.domain.SimulationEquityId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SimulationEquityRepository extends JpaRepository<SimulationEquity, SimulationEquityId> {

    Page<SimulationEquity> findAllBySimulationRunSimulationIdOrderByIdValuationAtAsc(UUID simulationId, Pageable pageable);
}
