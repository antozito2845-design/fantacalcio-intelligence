-- ============================================================
-- V36 - DATA FRESHNESS & SOURCE MONITORING
-- ============================================================

CREATE TABLE data_source_freshness_policies (
    source VARCHAR(30) PRIMARY KEY,
    fresh_max_hours INTEGER NOT NULL,
    stale_after_hours INTEGER NOT NULL,
    required_for_refresh BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_data_source_freshness_source
        CHECK (source IN ('FANTACALCIO', 'GAZZETTA', 'FANTAMARKT')),

    CONSTRAINT ck_data_source_freshness_hours
        CHECK (
            fresh_max_hours > 0
            AND stale_after_hours > fresh_max_hours
        )
);

INSERT INTO data_source_freshness_policies (
    source,
    fresh_max_hours,
    stale_after_hours,
    required_for_refresh
)
VALUES
    ('FANTACALCIO', 48, 120, TRUE),
    ('GAZZETTA', 72, 168, TRUE),
    ('FANTAMARKT', 168, 336, FALSE);

CREATE INDEX idx_import_batches_freshness_lookup
    ON import_batches (
        season_id,
        source,
        status,
        source_observed_at DESC,
        completed_at DESC,
        id DESC
    );

ALTER TABLE data_refresh_runs
ADD COLUMN source_freshness_status VARCHAR(20);

ALTER TABLE data_refresh_runs
ADD COLUMN source_freshness_checked_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE data_refresh_runs
ADD CONSTRAINT ck_data_refresh_source_freshness_status
CHECK (
    source_freshness_status IS NULL
    OR source_freshness_status IN ('READY', 'WARNING', 'BLOCKED')
);