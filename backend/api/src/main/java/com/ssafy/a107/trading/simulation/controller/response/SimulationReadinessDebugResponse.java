package com.ssafy.a107.trading.simulation.controller.response;

import com.ssafy.a107.trading.simulation.service.SimulationStrategyReadinessEvaluator;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SimulationReadinessDebugResponse(
        String state,
        Instant warmupFromInclusive,
        int warmupCandleCount,
        List<AssetReadinessResponse> assets
) {
    public static SimulationReadinessDebugResponse from(SimulationStrategyReadinessEvaluator.Evaluation evaluation) {
        return new SimulationReadinessDebugResponse(
                evaluation.state().name(),
                evaluation.fromInclusive(),
                evaluation.warmupCandleCount(),
                evaluation.assets().stream().map(AssetReadinessResponse::from).toList()
        );
    }

    public record AssetReadinessResponse(
            UUID assetId,
            String marketCode,
            boolean held,
            String state,
            int completedCandleCount,
            int priorCandleCount
    ) {
        private static AssetReadinessResponse from(SimulationStrategyReadinessEvaluator.AssetReadiness readiness) {
            return new AssetReadinessResponse(
                    readiness.assetId(), readiness.marketCode(), readiness.held(), readiness.state().name(),
                    readiness.completedCandleCount(), readiness.priorCandleCount()
            );
        }
    }
}
