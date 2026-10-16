CREATE TABLE fixture_import_batches (
    id BIGSERIAL PRIMARY KEY,

    season_id BIGINT NOT NULL,

    source VARCHAR(50) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,

    status VARCHAR(20) NOT NULL
        DEFAULT 'PROCESSING',

    total_rows INTEGER NOT NULL DEFAULT 0,
    valid_rows INTEGER NOT NULL DEFAULT 0,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    imported_fixtures INTEGER NOT NULL DEFAULT 0,

    error_message VARCHAR(2000),

    started_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    completed_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_fixture_import_batch_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT ck_fixture_import_batch_status
        CHECK (
            status IN (
                'PROCESSING',
                'COMPLETED',
                'FAILED'
            )
        ),

    CONSTRAINT ck_fixture_import_batch_counts
        CHECK (
            total_rows >= 0
            AND valid_rows >= 0
            AND invalid_rows >= 0
            AND imported_fixtures >= 0
        )
);

CREATE TABLE fixture_import_rows (
    id BIGSERIAL PRIMARY KEY,

    batch_id BIGINT NOT NULL,
    row_number INTEGER NOT NULL,

    matchday INTEGER,

    home_club_name VARCHAR(100),
    normalized_home_club VARCHAR(120),

    away_club_name VARCHAR(100),
    normalized_away_club VARCHAR(120),

    kickoff_at TIMESTAMP WITH TIME ZONE,
    fixture_status VARCHAR(20),

    home_goals INTEGER,
    away_goals INTEGER,

    valid BOOLEAN NOT NULL,
    validation_error VARCHAR(1000),

    raw_data JSONB NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fixture_import_row_batch
        FOREIGN KEY (batch_id)
        REFERENCES fixture_import_batches(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_fixture_import_row
        UNIQUE (batch_id, row_number),

    CONSTRAINT ck_fixture_import_row_number
        CHECK (row_number > 0),

    CONSTRAINT ck_fixture_import_matchday
        CHECK (
            matchday IS NULL
            OR matchday BETWEEN 1 AND 38
        ),

    CONSTRAINT ck_fixture_import_status
        CHECK (
            fixture_status IS NULL
            OR fixture_status IN (
                'SCHEDULED',
                'LIVE',
                'FINISHED',
                'POSTPONED',
                'CANCELLED'
            )
        ),

    CONSTRAINT ck_fixture_import_goals
        CHECK (
            (home_goals IS NULL OR home_goals >= 0)
            AND
            (away_goals IS NULL OR away_goals >= 0)
        )
);

CREATE INDEX idx_fixture_import_batches_season
    ON fixture_import_batches(
        season_id,
        started_at DESC
    );

CREATE INDEX idx_fixture_import_batches_status
    ON fixture_import_batches(status);

CREATE INDEX idx_fixture_import_rows_batch
    ON fixture_import_rows(
        batch_id,
        row_number
    );

CREATE INDEX idx_fixture_import_rows_invalid
    ON fixture_import_rows(
        batch_id,
        valid
    );