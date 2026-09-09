package com.ssafy.a107.trading.simulation.repository;

import com.ssafy.a107.trading.simulation.domain.SimulationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SimulationResultRepository extends JpaRepository<SimulationResult, UUID> {
}
