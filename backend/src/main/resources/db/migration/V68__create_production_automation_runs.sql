CREATE TABLE automation_runs (
    id BIGSERIAL PRIMARY KEY,

    season_id BIGINT NOT NULL
        REFERENCES seasons(id),

    trigger_type VARCHAR(20) NOT NULL
        DEFAULT 'SCHEDULED',

    status VARCHAR(20) NOT NULL
        DEFAULT 'RUNNING',

    stage VARCHAR(30) NOT NULL
        DEFAULT 'STARTING',

    listone_run_id BIGINT,

    ingestion_run_count INTEGER NOT NULL
        DEFAULT 0,

    refresh_run_id BIGINT,

    started_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    last_heartbeat_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    completed_at TIMESTAMP WITH TIME ZONE,

    error_message VARCHAR(2000),

    CONSTRAINT ck_automation_run_trigger
        CHECK (
            trigger_type IN (
                'SCHEDULED'
            )
        ),

    CONSTRAINT ck_automation_run_status
        CHECK (
            status IN (
                'RUNNING',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_automation_run_stage
        CHECK (
            stage IN (
                'STARTING',
                'LISTONE',
                'INGESTION',
                'REFRESH',
                'COMPLETED'
            )
        ),

    CONSTRAINT ck_automation_ingestion_count
        CHECK (
            ingestion_run_count >= 0
        )
);

CREATE UNIQUE INDEX uq_automation_run_running_per_season
    ON automation_runs(season_id)
    WHERE status = 'RUNNING';

CREATE INDEX idx_automation_runs_season_started
    ON automation_runs(
        season_id,
        started_at DESC,
        id DESC
    );

CREATE INDEX idx_automation_runs_status
    ON automation_runs(status);