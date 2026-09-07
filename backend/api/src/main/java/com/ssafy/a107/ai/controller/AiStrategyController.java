package com.ssafy.a107.ai.controller;

import com.ssafy.a107.ai.controller.request.TranslateStrategyRequest;
import com.ssafy.a107.ai.controller.response.TranslateStrategyResponse;
import com.ssafy.a107.ai.service.AiStrategyTranslationService;
import com.ssafy.a107.auth.jwt.MemberPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/strategies")
public class AiStrategyController {

    private final AiStrategyTranslationService aiStrategyTranslationService;

    public AiStrategyController(AiStrategyTranslationService aiStrategyTranslationService) {
        this.aiStrategyTranslationService = aiStrategyTranslationService;
    }

    @PostMapping("/translate")
    public TranslateStrategyResponse translateStrategy(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Valid @RequestBody TranslateStrategyRequest request
    ) {
        return TranslateStrategyResponse.from(aiStrategyTranslationService.translate(
                principal.memberId(),
                request.userInput(),
                request.currentStrategy()
        ));
    }
}
