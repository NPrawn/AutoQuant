CREATE INDEX idx_simulation_run_status_timeframe_simulation_id
    ON trading.simulation_run (status, timeframe, simulation_id);
