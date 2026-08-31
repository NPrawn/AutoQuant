package com.ssafy.a107.asset.controller.request;

import com.ssafy.a107.asset.domain.Exchange;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAssetRequest(
        @NotNull(message = "거래소는 필수입니다.")
        Exchange exchange,

        @NotBlank(message = "거래소 마켓 코드는 필수입니다.")
        @Size(max = 30, message = "거래소 마켓 코드는 30자 이하여야 합니다.")
        @Pattern(
                regexp = "^[A-Z0-9]+-[A-Z0-9]+$",
                message = "거래소 마켓 코드는 KRW-BTC 형식의 대문자 코드여야 합니다."
        )
        String exchangeMarketCode
) {
}
