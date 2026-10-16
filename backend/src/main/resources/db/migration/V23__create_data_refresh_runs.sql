CREATE TABLE data_refresh_runs (
    id BIGSERIAL PRIMARY KEY,

    season_id BIGINT NOT NULL,

    trigger_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',

    reconciliation_matched_players INTEGER NOT NULL DEFAULT 0,
    generated_evaluations INTEGER NOT NULL DEFAULT 0,
    generated_club_ratings INTEGER NOT NULL DEFAULT 0,

    started_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    completed_at TIMESTAMP WITH TIME ZONE,

    error_message VARCHAR(2000),

    CONSTRAINT fk_data_refresh_run_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT ck_data_refresh_trigger_type
        CHECK (
            trigger_type IN (
                'MANUAL',
                'SCHEDULED'
            )
        ),

    CONSTRAINT ck_data_refresh_status
        CHECK (
            status IN (
                'RUNNING',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_data_refresh_counts
        CHECK (
            reconciliation_matched_players >= 0
            AND generated_evaluations >= 0
            AND generated_club_ratings >= 0
        )
);

CREATE UNIQUE INDEX uq_data_refresh_single_running
ON data_refresh_runs(status)
WHERE status = 'RUNNING';

CREATE INDEX idx_data_refresh_runs_season_started
ON data_refresh_runs(
    season_id,
    started_at DESC
);

CREATE INDEX idx_data_refresh_runs_status
ON data_refresh_runs(status);