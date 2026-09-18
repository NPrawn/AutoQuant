CREATE TABLE trading.simulation_rebalance_attempt (
    simulation_id UUID        NOT NULL,
    execution_at  TIMESTAMPTZ NOT NULL,
    status        VARCHAR(30) NOT NULL,
    reason        VARCHAR(100) NULL,
    updated_at    TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_simulation_rebalance_attempt PRIMARY KEY (simulation_id, execution_at),
    CONSTRAINT fk_simulation_rebalance_attempt_run FOREIGN KEY (simulation_id)
        REFERENCES trading.simulation_run (simulation_id) ON DELETE CASCADE,
    CONSTRAINT ck_simulation_rebalance_attempt_status CHECK (
        status IN ('COMPLETED', 'WAITING_FOR_DATA', 'SKIPPED')
    )
);

CREATE INDEX idx_simulation_rebalance_attempt_execution_status
    ON trading.simulation_rebalance_attempt (execution_at, status);
