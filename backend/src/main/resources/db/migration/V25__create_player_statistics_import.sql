CREATE TABLE player_statistics_import_batches (
    id BIGSERIAL PRIMARY KEY,

    source VARCHAR(30) NOT NULL,
    season_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,

    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',

    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    imported_statistics INTEGER NOT NULL DEFAULT 0,

    started_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,

    CONSTRAINT fk_statistics_import_batch_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT ck_statistics_import_batch_source
        CHECK (
            source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT'
            )
        ),

    CONSTRAINT ck_statistics_import_batch_status
        CHECK (
            status IN (
                'PROCESSING',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_statistics_import_batch_counts
        CHECK (
            total_rows >= 0
            AND valid_rows >= 0
            AND invalid_rows >= 0
            AND imported_statistics >= 0
            AND valid_rows + invalid_rows <= total_rows
            AND imported_statistics <= valid_rows
        )
);

CREATE TABLE player_statistics_import_rows (
    id BIGSERIAL PRIMARY KEY,

    batch_id BIGINT NOT NULL,
    row_number INTEGER NOT NULL,

    external_id VARCHAR(150),
    external_name VARCHAR(150),
    normalized_name VARCHAR(150),

    club_name VARCHAR(100),
    normalized_club_name VARCHAR(100),

    resolved_player_id BIGINT,
    resolved_club_id BIGINT,

    valid BOOLEAN NOT NULL DEFAULT TRUE,
    validation_error TEXT,

    raw_data JSONB NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_statistics_import_row_batch
        FOREIGN KEY (batch_id)
        REFERENCES player_statistics_import_batches(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_statistics_import_row_player
        FOREIGN KEY (resolved_player_id)
        REFERENCES players(id),

    CONSTRAINT fk_statistics_import_row_club
        FOREIGN KEY (resolved_club_id)
        REFERENCES clubs(id),

    CONSTRAINT uq_statistics_import_batch_row
        UNIQUE (batch_id, row_number)
);

CREATE INDEX idx_statistics_import_batches_lookup
    ON player_statistics_import_batches(
        season_id,
        source,
        status,
        completed_at DESC
    );

CREATE INDEX idx_statistics_import_rows_batch_valid
    ON player_statistics_import_rows(
        batch_id,
        valid,
        row_number
    );

CREATE INDEX idx_statistics_import_rows_player
    ON player_statistics_import_rows(
        resolved_player_id
    );