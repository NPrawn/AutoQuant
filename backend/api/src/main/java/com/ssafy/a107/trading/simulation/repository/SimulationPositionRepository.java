package com.ssafy.a107.trading.simulation.repository;

import com.ssafy.a107.trading.simulation.domain.SimulationPosition;
import com.ssafy.a107.trading.simulation.domain.SimulationPositionId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SimulationPositionRepository extends JpaRepository<SimulationPosition, SimulationPositionId> {

    @EntityGraph(attributePaths = "asset")
    List<SimulationPosition> findAllBySimulationRunSimulationIdOrderByIdAssetIdAsc(UUID simulationId);

    long deleteAllBySimulationRunSimulationId(UUID simulationId);
}
