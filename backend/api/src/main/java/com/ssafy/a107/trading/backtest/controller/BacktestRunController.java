package com.ssafy.a107.trading.backtest.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.trading.backtest.controller.request.CreateBacktestRunRequest;
import com.ssafy.a107.trading.backtest.controller.response.BacktestRunCreatedResponse;
import com.ssafy.a107.trading.backtest.controller.response.BacktestRunPageResponse;
import com.ssafy.a107.trading.backtest.controller.response.BacktestRunResponse;
import com.ssafy.a107.trading.backtest.service.BacktestRunCommandService;
import com.ssafy.a107.trading.backtest.service.BacktestRunReadService;
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

import java.util.UUID;

@Validated
@RestController
@RequestMapping("/backtests")
public class BacktestRunController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final BacktestRunCommandService backtestRunCommandService;
    private final BacktestRunReadService backtestRunReadService;

    public BacktestRunController(
            BacktestRunCommandService backtestRunCommandService,
            BacktestRunReadService backtestRunReadService
    ) {
        this.backtestRunCommandService = backtestRunCommandService;
        this.backtestRunReadService = backtestRunReadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BacktestRunCreatedResponse createBacktestRun(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody CreateBacktestRunRequest request
    ) {
        return BacktestRunCreatedResponse.from(
                backtestRunCommandService.createBacktestRun(principal.memberId(), request.toCommand())
        );
    }

    @GetMapping
    public BacktestRunPageResponse getBacktestRuns(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) int size
    ) {
        return BacktestRunPageResponse.from(
                backtestRunReadService.getBacktestRuns(principal.memberId(), PageRequest.of(page, size))
        );
    }

    @GetMapping("/{backtestId}")
    public BacktestRunResponse getBacktestRun(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable UUID backtestId
    ) {
        return BacktestRunResponse.from(
                backtestRunReadService.getBacktestRun(principal.memberId(), backtestId)
        );
    }
}
