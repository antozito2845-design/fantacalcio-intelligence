ALTER TABLE import_batches
ADD COLUMN error_message TEXT;

ALTER TABLE import_batches
ADD COLUMN reconciliation_started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE import_batches
ADD COLUMN reconciliation_completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE import_batches
ADD COLUMN reconciled_rows INTEGER NOT NULL DEFAULT 0;

ALTER TABLE import_batches
ADD CONSTRAINT ck_import_batches_row_counts
CHECK (
    total_rows >= 0
    AND valid_rows >= 0
    AND invalid_rows >= 0
    AND reconciled_rows >= 0
    AND valid_rows + invalid_rows <= total_rows
);

CREATE INDEX idx_import_batches_season_source_status
ON import_batches (
    season_id,
    source,
    status,
    completed_at DESC
);

CREATE INDEX idx_import_rows_batch_valid
ON player_import_rows (
    batch_id,
    valid,
    normalized_name,
    normalized_club_name
);

CREATE UNIQUE INDEX uq_player_source_external_id
ON player_source_mappings (
    source,
    external_id
)
WHERE external_id IS NOT NULL;