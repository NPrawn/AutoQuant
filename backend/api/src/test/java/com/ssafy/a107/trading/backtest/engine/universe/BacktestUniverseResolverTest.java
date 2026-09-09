package com.ssafy.a107.trading.backtest.engine.universe;

import com.ssafy.a107.asset.domain.Asset;
import com.ssafy.a107.asset.domain.Exchange;
import com.ssafy.a107.asset.repository.AssetRepository;
import com.ssafy.a107.trading.backtest.domain.BacktestRun;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class BacktestUniverseResolverTest {

    private static final Instant PERIOD_START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant EVALUATION_AT = Instant.parse("2026-01-15T00:00:00Z");

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private BacktestRun backtestRun;

    @Mock
    private Asset asset;

    @DisplayName("평가 시각에 거래 지원 중인 Upbit 기준 통화 종목을 조회한다")
    @Test
    void resolveAvailableUniverseAtEvaluationTime() {
        given(backtestRun.getPeriodStart()).willReturn(PERIOD_START);
        given(backtestRun.getPeriodEnd()).willReturn(PERIOD_END);
        given(backtestRun.getQuoteCurrency()).willReturn("KRW");
        given(assetRepository.findAllAvailableAt(Exchange.UPBIT, "KRW", EVALUATION_AT))
                .willReturn(new ArrayList<>(List.of(asset)));

        List<Asset> universe = resolver().resolve(backtestRun, EVALUATION_AT);

        assertThat(universe).containsExactly(asset);
        assertThatThrownBy(() -> universe.add(asset))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @DisplayName("백테스트 평가 기간 밖의 시각으로는 유니버스를 조회할 수 없다")
    @Test
    void rejectEvaluationTimeOutsideBacktestPeriod() {
        given(backtestRun.getPeriodStart()).willReturn(PERIOD_START);
        given(backtestRun.getPeriodEnd()).willReturn(PERIOD_END);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> resolver().resolve(backtestRun, PERIOD_END))
                .withMessage("Universe evaluation time must be within backtest period");

        then(assetRepository).shouldHaveNoInteractions();
    }

    @DisplayName("백테스트 기간 중 거래 지원 이력이 있는 종목을 후보 유니버스로 조회한다")
    @Test
    void resolveCandidateUniverseDuringBacktestPeriod() {
        given(backtestRun.getQuoteCurrency()).willReturn("KRW");
        given(backtestRun.getPeriodStart()).willReturn(PERIOD_START);
        given(backtestRun.getPeriodEnd()).willReturn(PERIOD_END);
        given(assetRepository.findAllAvailableDuring(
                Exchange.UPBIT,
                "KRW",
                PERIOD_START,
                PERIOD_END
        )).willReturn(List.of(asset));

        List<Asset> candidates = resolver().resolveCandidates(backtestRun);

        assertThat(candidates).containsExactly(asset);
        assertThatThrownBy(() -> candidates.add(asset))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private BacktestUniverseResolver resolver() {
        return new BacktestUniverseResolver(assetRepository);
    }
}
