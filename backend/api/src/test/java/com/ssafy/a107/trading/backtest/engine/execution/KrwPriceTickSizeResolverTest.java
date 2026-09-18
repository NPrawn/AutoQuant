package com.ssafy.a107.trading.backtest.engine.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KrwPriceTickSizeResolverTest {

    private final KrwPriceTickSizeResolver resolver = new KrwPriceTickSizeResolver();

    @DisplayName("원화 마켓 가격 구간에 맞는 호가 단위를 반환한다")
    @ParameterizedTest
    @MethodSource("priceAndTickSizes")
    void resolveKrwTickSize(BigDecimal price, BigDecimal expectedTickSize) {
        BigDecimal tickSize = resolver.resolve(price);

        assertThat(tickSize).isEqualByComparingTo(expectedTickSize);
    }

    private static Stream<Arguments> priceAndTickSizes() {
        return Stream.of(
                arguments("1000000", "1000"),
                arguments("500000", "500"),
                arguments("100000", "100"),
                arguments("50000", "50"),
                arguments("10000", "10"),
                arguments("5000", "5"),
                arguments("1000", "1"),
                arguments("100", "1"),
                arguments("10", "0.1"),
                arguments("1", "0.01"),
                arguments("0.1", "0.001"),
                arguments("0.01", "0.0001"),
                arguments("0.001", "0.00001"),
                arguments("0.0001", "0.000001"),
                arguments("0.00001", "0.0000001"),
                arguments("0.000001", "0.00000001")
        );
    }

    private static Arguments arguments(String price, String tickSize) {
        return Arguments.of(new BigDecimal(price), new BigDecimal(tickSize));
    }
}
