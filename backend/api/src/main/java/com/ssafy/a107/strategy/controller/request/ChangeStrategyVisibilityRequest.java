package com.ssafy.a107.strategy.controller.request;

import jakarta.validation.constraints.NotNull;

/* 공개와 비공개를 한 엔드포인트에서 다룬다. 값을 주지 않으면 어느 쪽인지 정할 수 없어 거절한다. */
public record ChangeStrategyVisibilityRequest(@NotNull Boolean published) {
}
