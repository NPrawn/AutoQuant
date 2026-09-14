package com.ssafy.a107.strategy.controller;

import com.ssafy.a107.common.exception.ApiErrorCode;
import com.ssafy.a107.common.exception.ApiException;
import com.ssafy.a107.strategy.controller.response.AssetFactorsResponse;
import com.ssafy.a107.strategy.domain.dsl.Timeframe;
import com.ssafy.a107.strategy.service.AssetFactorReadService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/assets/{assetId}/factors")
public class AssetFactorController {

    private final AssetFactorReadService assetFactorReadService;

    public AssetFactorController(AssetFactorReadService assetFactorReadService) {
        this.assetFactorReadService = assetFactorReadService;
    }

    @GetMapping
    public AssetFactorsResponse getCurrentFactors(
            @PathVariable UUID assetId,
            @RequestParam(defaultValue = "1D") String timeframe
    ) {
        Timeframe parsed = parseTimeframe(timeframe);
        return AssetFactorsResponse.from(assetId, parsed, assetFactorReadService.findCurrent(assetId, parsed));
    }

    // Timeframe 은 "1D" 표기로 오고 enum 이름과 달라 스프링 기본 변환이 받지 못한다. 여기서 바꾼다.
    private Timeframe parseTimeframe(String timeframe) {
        try {
            return Timeframe.fromValue(timeframe);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, exception);
        }
    }
}
