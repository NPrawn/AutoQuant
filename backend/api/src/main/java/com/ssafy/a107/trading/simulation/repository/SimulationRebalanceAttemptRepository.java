package com.ssafy.a107.trading.simulation.repository;

import com.ssafy.a107.trading.simulation.domain.SimulationRebalanceAttempt;
import com.ssafy.a107.trading.simulation.domain.SimulationRebalanceAttemptId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRebalanceAttemptRepository
        extends JpaRepository<SimulationRebalanceAttempt, SimulationRebalanceAttemptId> {
}
