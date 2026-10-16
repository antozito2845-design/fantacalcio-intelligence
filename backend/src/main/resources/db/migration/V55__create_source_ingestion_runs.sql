ALTER TABLE player_statistics_import_batches
    ADD COLUMN source_observed_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE fixture_import_batches
    ADD COLUMN source_observed_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE source_ingestion_runs (
    id BIGSERIAL PRIMARY KEY,
    feed_id VARCHAR(100) NOT NULL,
    feed_type VARCHAR(20) NOT NULL CHECK (feed_type IN ('PLAYERS','STATISTICS','FIXTURES')),
    source VARCHAR(30) NOT NULL,
    season_id BIGINT NOT NULL REFERENCES seasons(id),
    sanitized_url VARCHAR(1000) NOT NULL,
    status VARCHAR(30) NOT NULL CHECK (status IN ('STARTED','COMPLETED','SKIPPED_UNCHANGED','FAILED')),
    http_status INTEGER,
    payload_bytes BIGINT,
    payload_sha256 CHAR(64),
    import_batch_id BIGINT,
    error_message VARCHAR(2000),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_source_ingestion_runs_feed_season
    ON source_ingestion_runs(feed_id, season_id, started_at DESC, id DESC);
CREATE UNIQUE INDEX uq_source_ingestion_run_in_progress
    ON source_ingestion_runs(feed_id, season_id) WHERE status='STARTED';
