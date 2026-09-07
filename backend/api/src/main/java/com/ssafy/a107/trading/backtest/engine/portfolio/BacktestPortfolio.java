package com.ssafy.a107.trading.backtest.engine.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public class BacktestPortfolio {

    private BigDecimal cashBalance;
    private final Map<UUID, BacktestPosition> positions = new TreeMap<>();

    public BacktestPortfolio(BigDecimal initialCash) {
        this.cashBalance = requirePositive(
                initialCash,
                "Initial cash must be greater than zero"
        );
    }

    public BigDecimal getCashBalance() {
        return cashBalance;
    }

    public List<BacktestPosition> getPositions() {
        return List.copyOf(positions.values());
    }

    public Optional<BacktestPosition> findPosition(UUID assetId) {
        return Optional.ofNullable(
                positions.get(Objects.requireNonNull(assetId))
        );
    }

    private BigDecimal requirePositive(
            BigDecimal value,
            String message
    ) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
