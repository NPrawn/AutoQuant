package com.ssafy.a107.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorCodeTest {

    @DisplayName("API 에러 코드는 서로 중복되지 않는다")
    @Test
    void keepErrorCodesUnique() {
        List<String> errorCodes = Arrays.stream(ApiErrorCode.values())
                .map(ApiErrorCode::getCode)
                .toList();

        assertThat(errorCodes).doesNotHaveDuplicates();
    }
}
