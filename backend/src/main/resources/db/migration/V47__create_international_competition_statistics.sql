-- Competition-preserving international statistics. Statistical links are
-- intentionally isolated from player_source_mappings and sources_count.

ALTER TABLE player_statistics_import_batches
    DROP CONSTRAINT ck_statistics_import_batch_source;

ALTER TABLE player_statistics_import_batches
    ADD CONSTRAINT ck_statistics_import_batch_source
        CHECK (source IN ('FANTACALCIO', 'GAZZETTA', 'FANTAMARKT', 'FBREF', 'TRANSFERMARKT'));

CREATE TABLE club_statistical_source_links (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    source VARCHAR(30) NOT NULL,
    external_id VARCHAR(150) NOT NULL,
    external_name VARCHAR(150) NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_club_statistical_source_external UNIQUE (source, external_id),
    CONSTRAINT ck_club_statistical_source CHECK (source = 'TRANSFERMARKT')
);

CREATE INDEX idx_club_statistical_source_club
    ON club_statistical_source_links(club_id);

CREATE TABLE player_competition_season_statistics (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players(id),
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    season_id BIGINT NOT NULL REFERENCES seasons(id),
    source VARCHAR(30) NOT NULL,
    source_external_id VARCHAR(150) NOT NULL,
    competition_code VARCHAR(30) NOT NULL,
    competition_name VARCHAR(150) NOT NULL,
    competition_type VARCHAR(30) NOT NULL,
    country_code CHAR(2) NOT NULL,
    appearances INTEGER,
    starts INTEGER,
    minutes_played INTEGER,
    goals INTEGER,
    assists INTEGER,
    yellow_cards INTEGER,
    second_yellow_cards INTEGER,
    red_cards INTEGER,
    expected_goals NUMERIC(7,3),
    expected_assists NUMERIC(7,3),
    source_url TEXT NOT NULL,
    source_page_sha256 CHAR(64) NOT NULL,
    import_batch_id BIGINT NOT NULL REFERENCES player_statistics_import_batches(id),
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_player_competition_season_statistics
        UNIQUE (player_id, season_id, source, competition_code, club_id),
    CONSTRAINT ck_competition_statistics_source CHECK (source = 'TRANSFERMARKT'),
    CONSTRAINT ck_competition_statistics_type CHECK (
        competition_type IN ('DOMESTIC_LEAGUE', 'DOMESTIC_CUP', 'INTERNATIONAL_CUP', 'OTHER')
    ),
    CONSTRAINT ck_competition_statistics_non_negative CHECK (
        (appearances IS NULL OR appearances >= 0)
        AND (starts IS NULL OR starts >= 0)
        AND (minutes_played IS NULL OR minutes_played >= 0)
        AND (goals IS NULL OR goals >= 0)
        AND (assists IS NULL OR assists >= 0)
        AND (yellow_cards IS NULL OR yellow_cards >= 0)
        AND (second_yellow_cards IS NULL OR second_yellow_cards >= 0)
        AND (red_cards IS NULL OR red_cards >= 0)
        AND (expected_goals IS NULL OR expected_goals >= 0)
        AND (expected_assists IS NULL OR expected_assists >= 0)
    ),
    CONSTRAINT ck_competition_statistics_starts CHECK (
        starts IS NULL OR appearances IS NULL OR starts <= appearances
    )
);

CREATE INDEX idx_competition_statistics_player_season
    ON player_competition_season_statistics(player_id, season_id);

CREATE INDEX idx_competition_statistics_competition
    ON player_competition_season_statistics(competition_code, season_id, source);
