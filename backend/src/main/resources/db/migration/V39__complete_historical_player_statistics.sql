ALTER TABLE player_season_statistics
    ALTER COLUMN appearances DROP NOT NULL,
    ALTER COLUMN appearances DROP DEFAULT,
    ALTER COLUMN starts DROP NOT NULL,
    ALTER COLUMN starts DROP DEFAULT,
    ALTER COLUMN minutes_played DROP NOT NULL,
    ALTER COLUMN minutes_played DROP DEFAULT,
    ALTER COLUMN goals DROP NOT NULL,
    ALTER COLUMN goals DROP DEFAULT,
    ALTER COLUMN assists DROP NOT NULL,
    ALTER COLUMN assists DROP DEFAULT,
    ALTER COLUMN penalties_scored DROP NOT NULL,
    ALTER COLUMN penalties_scored DROP DEFAULT,
    ALTER COLUMN penalties_missed DROP NOT NULL,
    ALTER COLUMN penalties_missed DROP DEFAULT,
    ALTER COLUMN yellow_cards DROP NOT NULL,
    ALTER COLUMN yellow_cards DROP DEFAULT,
    ALTER COLUMN red_cards DROP NOT NULL,
    ALTER COLUMN red_cards DROP DEFAULT,
    ALTER COLUMN own_goals DROP NOT NULL,
    ALTER COLUMN own_goals DROP DEFAULT,
    ALTER COLUMN goals_conceded DROP NOT NULL,
    ALTER COLUMN goals_conceded DROP DEFAULT,
    ALTER COLUMN clean_sheets DROP NOT NULL,
    ALTER COLUMN clean_sheets DROP DEFAULT,
    ALTER COLUMN penalties_saved DROP NOT NULL,
    ALTER COLUMN penalties_saved DROP DEFAULT,
    ADD COLUMN source_external_id VARCHAR(150),
    ADD COLUMN penalties_taken INTEGER,
    ADD COLUMN saves INTEGER,
    ADD COLUMN source_updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN import_batch_id BIGINT,
    ADD CONSTRAINT fk_statistics_import_batch
        FOREIGN KEY (import_batch_id)
        REFERENCES player_statistics_import_batches(id),
    ADD CONSTRAINT ck_statistics_starts_appearances
        CHECK (starts IS NULL OR appearances IS NULL OR starts <= appearances),
    ADD CONSTRAINT ck_statistics_extended_non_negative
        CHECK (
            (appearances IS NULL OR appearances >= 0)
            AND (starts IS NULL OR starts >= 0)
            AND (minutes_played IS NULL OR minutes_played >= 0)
            AND (goals IS NULL OR goals >= 0)
            AND (assists IS NULL OR assists >= 0)
            AND (penalties_taken IS NULL OR penalties_taken >= 0)
            AND (penalties_scored IS NULL OR penalties_scored >= 0)
            AND (penalties_missed IS NULL OR penalties_missed >= 0)
            AND (yellow_cards IS NULL OR yellow_cards >= 0)
            AND (red_cards IS NULL OR red_cards >= 0)
            AND (own_goals IS NULL OR own_goals >= 0)
            AND (goals_conceded IS NULL OR goals_conceded >= 0)
            AND (clean_sheets IS NULL OR clean_sheets >= 0)
            AND (saves IS NULL OR saves >= 0)
            AND (penalties_saved IS NULL OR penalties_saved >= 0)
        );

CREATE INDEX idx_statistics_season_source
    ON player_season_statistics(season_id, source);

CREATE INDEX idx_statistics_import_batch
    ON player_season_statistics(import_batch_id)
    WHERE import_batch_id IS NOT NULL;
