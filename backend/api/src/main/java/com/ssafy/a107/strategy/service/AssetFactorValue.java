package com.ssafy.a107.strategy.service;

import com.ssafy.a107.strategy.domain.dsl.FactorType;

import java.math.BigDecimal;

// 봉이 기간을 채우지 못해 계산할 수 없으면 value 가 null 이다. 오류가 아니라 아직 값이 없는 것이다.
public record AssetFactorValue(FactorType factor, int period, BigDecimal value) {
}
