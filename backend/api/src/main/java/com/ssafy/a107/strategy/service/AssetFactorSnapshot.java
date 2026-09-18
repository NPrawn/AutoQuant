package com.ssafy.a107.strategy.service;

import java.time.Instant;
import java.util.List;

// asOf 는 값을 만든 마지막 확정 봉의 시작 시각이다. 봉이 하나도 없으면 null 이다.
public record AssetFactorSnapshot(Instant asOf, List<AssetFactorValue> values) {
}
