package com.ssafy.a107.strategy.controller.response;

import com.ssafy.a107.strategy.domain.dsl.FactorType;
import com.ssafy.a107.strategy.service.AssetFactorValue;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record FactorValueResponse(FactorType factor, int period, BigDecimal value) {

    // 계산 정밀도를 그대로 실으면 자리수가 길어진다. 표시에 필요한 만큼만 남긴다.
    private static final int DISPLAY_SCALE = 6;

    public static FactorValueResponse from(AssetFactorValue value) {
        return new FactorValueResponse(value.factor(), value.period(), round(value.value()));
    }

    private static BigDecimal round(BigDecimal value) {
        if (value == null) {
            return null;
        }

        BigDecimal rounded = value.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.scale() < 0 ? rounded.setScale(0) : rounded;
    }
}
