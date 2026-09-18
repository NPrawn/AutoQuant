package com.ssafy.a107.trading.simulation.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.trading.simulation.controller.response.SimulationReadinessDebugResponse;
import com.ssafy.a107.trading.simulation.service.SimulationReadinessDebugService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 로컬·스테이징에서만 켜는 읽기 전용 준비 상태 진단 API다.
 */
@Profile("simulation-debug")
@ConditionalOnProperty(prefix = "simulation.debug", name = "enabled", havingValue = "true")
@RestController
@RequestMapping("/debug/simulations")
public class SimulationReadinessDebugController {

    private final SimulationReadinessDebugService simulationReadinessDebugService;

    public SimulationReadinessDebugController(SimulationReadinessDebugService simulationReadinessDebugService) {
        this.simulationReadinessDebugService = simulationReadinessDebugService;
    }

    @GetMapping("/{simulationId}/readiness")
    public SimulationReadinessDebugResponse inspect(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID simulationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant executionAt,
            @RequestParam(defaultValue = "false") boolean hideLatestCompletedCandle
    ) {
        return SimulationReadinessDebugResponse.from(simulationReadinessDebugService.inspect(
                principal.memberId(), simulationId, executionAt, hideLatestCompletedCandle
        ));
    }
}
