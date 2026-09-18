package com.ssafy.a107.strategy.controller;

import com.ssafy.a107.auth.jwt.MemberPrincipal;
import com.ssafy.a107.strategy.controller.request.ChangeStrategyVisibilityRequest;
import com.ssafy.a107.strategy.controller.request.CreateStrategyRequest;
import com.ssafy.a107.strategy.controller.response.StrategyResponse;
import com.ssafy.a107.strategy.service.StrategyCommandService;
import com.ssafy.a107.strategy.service.StrategyReadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/strategies")
public class StrategyController {

    private final StrategyCommandService strategyCommandService;
    private final StrategyReadService strategyReadService;

    public StrategyController(
            StrategyCommandService strategyCommandService,
            StrategyReadService strategyReadService
    ) {
        this.strategyCommandService = strategyCommandService;
        this.strategyReadService = strategyReadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StrategyResponse createStrategy(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody CreateStrategyRequest request
    ) {
        return StrategyResponse.from(strategyCommandService.createStrategy(
                principal.memberId(),
                request.name(),
                request.description(),
                request.strategyDsl()
        ));
    }

    @GetMapping
    public List<StrategyResponse> getStrategies(@AuthenticationPrincipal MemberPrincipal principal) {
        return strategyReadService.getStrategies(principal.memberId())
                .stream()
                .map(StrategyResponse::from)
                .toList();
    }

    @GetMapping("/{strategyId}")
    public StrategyResponse getStrategy(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long strategyId
    ) {
        return StrategyResponse.from(strategyReadService.getStrategy(principal.memberId(), strategyId));
    }

    @PatchMapping("/{strategyId}/visibility")
    public StrategyResponse changeVisibility(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long strategyId,
            @Valid @RequestBody ChangeStrategyVisibilityRequest request
    ) {
        return StrategyResponse.from(
                strategyCommandService.changeVisibility(principal.memberId(), strategyId, request.published()));
    }

    @PostMapping("/{strategyId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public StrategyResponse copyStrategy(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long strategyId
    ) {
        return StrategyResponse.from(strategyCommandService.copyStrategy(principal.memberId(), strategyId));
    }

    @DeleteMapping("/{strategyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStrategy(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long strategyId
    ) {
        strategyCommandService.deleteStrategy(principal.memberId(), strategyId);
    }
}
