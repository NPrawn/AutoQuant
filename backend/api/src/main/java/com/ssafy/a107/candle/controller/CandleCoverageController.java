package com.ssafy.a107.candle.controller;

import com.ssafy.a107.candle.controller.request.CandleCoverageRequest;
import com.ssafy.a107.candle.controller.response.CandleCoverageResponse;
import com.ssafy.a107.candle.domain.CandleIntervals;
import com.ssafy.a107.candle.service.CandleCoverageFacade;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * 종목별 조회가 아니라 수집 종목 전체를 합친 값이라 /assets/{assetId}/candles 아래에 두지 않는다.
 */
@RestController
@RequestMapping("/candles")
public class CandleCoverageController {

    private final CandleCoverageFacade facade;

    public CandleCoverageController(CandleCoverageFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/coverage")
    public CandleCoverageResponse getCandleCoverage(@Valid @ModelAttribute CandleCoverageRequest request) {
        CandleIntervals.requireSupported(request.intervalMinutes());
        return CandleCoverageResponse.from(request.intervalMinutes(), facade.findCoverage(request.intervalMinutes()));
    }
}
