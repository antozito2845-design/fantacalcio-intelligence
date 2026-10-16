CREATE TABLE import_batches (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(30) NOT NULL,
    season_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_import_batches_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT ck_import_batches_source
        CHECK (
            source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT'
            )
        ),

    CONSTRAINT ck_import_batches_status
        CHECK (
            status IN (
                'PROCESSING',
                'COMPLETED',
                'FAILED'
            )
        )
);

CREATE TABLE player_import_rows (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    row_number INTEGER NOT NULL,

    external_id VARCHAR(150),
    external_name VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,

    club_name VARCHAR(100),
    normalized_club_name VARCHAR(100),

    fantasy_role VARCHAR(5),
    quotation NUMERIC(10,2),
    fantasy_market_value NUMERIC(10,2),

    valid BOOLEAN NOT NULL DEFAULT TRUE,
    validation_error TEXT,

    raw_data JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_player_import_rows_batch
        FOREIGN KEY (batch_id)
        REFERENCES import_batches(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_import_row_role
        CHECK (
            fantasy_role IS NULL
            OR fantasy_role IN ('P', 'D', 'C', 'A')
        )
);

CREATE INDEX idx_import_rows_batch
    ON player_import_rows(batch_id);

CREATE INDEX idx_import_rows_normalized_name
    ON player_import_rows(normalized_name);

CREATE INDEX idx_import_rows_name_club
    ON player_import_rows(
        normalized_name,
        normalized_club_name
    );