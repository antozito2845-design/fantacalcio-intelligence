DROP INDEX IF EXISTS uq_data_refresh_single_running;

CREATE UNIQUE INDEX uq_data_refresh_running_per_season
    ON data_refresh_runs (season_id)
    WHERE status = 'RUNNING';
