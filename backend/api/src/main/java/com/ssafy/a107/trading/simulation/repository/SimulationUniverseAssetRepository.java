package com.ssafy.a107.trading.simulation.repository;

import com.ssafy.a107.trading.simulation.domain.SimulationUniverseAsset;
import com.ssafy.a107.trading.simulation.domain.SimulationUniverseAssetId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationUniverseAssetRepository
        extends JpaRepository<SimulationUniverseAsset, SimulationUniverseAssetId> {
}
