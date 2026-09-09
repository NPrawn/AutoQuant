package com.ssafy.a107.trading.simulation.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.trading.simulation.controller.request.CreateSimulationRunRequest;
import com.ssafy.a107.trading.simulation.controller.response.SimulationEquityPageResponse;
import com.ssafy.a107.trading.simulation.controller.response.SimulationPositionResponse;
import com.ssafy.a107.trading.simulation.controller.response.SimulationResultResponse;
import com.ssafy.a107.trading.simulation.controller.response.SimulationRunCreatedResponse;
import com.ssafy.a107.trading.simulation.controller.response.SimulationRunPageResponse;
import com.ssafy.a107.trading.simulation.controller.response.SimulationRunResponse;
import com.ssafy.a107.trading.simulation.controller.response.SimulationTradePageResponse;
import com.ssafy.a107.trading.simulation.service.SimulationRunCommandService;
import com.ssafy.a107.trading.simulation.service.SimulationRunReadService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/simulations")
public class SimulationRunController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final SimulationRunCommandService simulationRunCommandService;
    private final SimulationRunReadService simulationRunReadService;

    public SimulationRunController(SimulationRunCommandService simulationRunCommandService,
                                   SimulationRunReadService simulationRunReadService) {
        this.simulationRunCommandService = simulationRunCommandService;
        this.simulationRunReadService = simulationRunReadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SimulationRunCreatedResponse createSimulationRun(@AuthenticationPrincipal MemberPrincipal principal,
                                                            @Valid @RequestBody CreateSimulationRunRequest request) {
        return SimulationRunCreatedResponse.from(
                simulationRunCommandService.createSimulationRun(principal.memberId(), request.toCommand())
        );
    }

    @PostMapping("/{simulationId}/start")
    public SimulationRunResponse startSimulationRun(@AuthenticationPrincipal MemberPrincipal principal,
                                                    @PathVariable UUID simulationId) {
        return SimulationRunResponse.from(simulationRunCommandService.startSimulationRun(principal.memberId(), simulationId));
    }

    @PostMapping("/{simulationId}/stop")
    public SimulationRunResponse stopSimulationRun(@AuthenticationPrincipal MemberPrincipal principal,
                                                   @PathVariable UUID simulationId) {
        return SimulationRunResponse.from(simulationRunCommandService.stopSimulationRun(principal.memberId(), simulationId));
    }

    @GetMapping
    public SimulationRunPageResponse getSimulationRuns(@AuthenticationPrincipal MemberPrincipal principal,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return SimulationRunPageResponse.from(simulationRunReadService.getSimulationRuns(principal.memberId(), pageRequest(page, size)));
    }

    @GetMapping("/{simulationId}")
    public SimulationRunResponse getSimulationRun(@AuthenticationPrincipal MemberPrincipal principal,
                                                  @PathVariable UUID simulationId) {
        return SimulationRunResponse.from(simulationRunReadService.getSimulationRun(principal.memberId(), simulationId));
    }

    @GetMapping("/{simulationId}/positions")
    public List<SimulationPositionResponse> getSimulationPositions(@AuthenticationPrincipal MemberPrincipal principal,
                                                                    @PathVariable UUID simulationId) {
        return simulationRunReadService.getSimulationPositions(principal.memberId(), simulationId).stream()
                .map(SimulationPositionResponse::from).toList();
    }

    @GetMapping("/{simulationId}/trades")
    public SimulationTradePageResponse getSimulationTrades(@AuthenticationPrincipal MemberPrincipal principal,
                                                           @PathVariable UUID simulationId,
                                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                                           @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return SimulationTradePageResponse.from(simulationRunReadService.getSimulationTrades(principal.memberId(), simulationId, pageRequest(page, size)));
    }

    @GetMapping("/{simulationId}/equity")
    public SimulationEquityPageResponse getSimulationEquities(@AuthenticationPrincipal MemberPrincipal principal,
                                                               @PathVariable UUID simulationId,
                                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                                               @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return SimulationEquityPageResponse.from(simulationRunReadService.getSimulationEquities(principal.memberId(), simulationId, pageRequest(page, size)));
    }

    @GetMapping("/{simulationId}/result")
    public SimulationResultResponse getSimulationResult(@AuthenticationPrincipal MemberPrincipal principal,
                                                        @PathVariable UUID simulationId) {
        return SimulationResultResponse.from(simulationRunReadService.getSimulationResult(principal.memberId(), simulationId));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        return PageRequest.of(page, size);
    }
}
