package com.ssafy.a107.candle.controller;

import com.ssafy.a107.candle.controller.request.RecentMinuteCandlesRequest;
import com.ssafy.a107.candle.controller.response.RecentMinuteCandlesResponse;
import com.ssafy.a107.candle.service.RecentMinuteCandleFacade;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/assets/{assetId}/candles")
public class MinuteCandleController {

    private final RecentMinuteCandleFacade facade;

    public MinuteCandleController(RecentMinuteCandleFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/1m")
    public RecentMinuteCandlesResponse getRecentMinuteCandles(
            @PathVariable UUID assetId,
            @Valid @ModelAttribute RecentMinuteCandlesRequest request
    ) {
        return RecentMinuteCandlesResponse.from(assetId, facade.findRecent(assetId, request.limit()));
    }
}
