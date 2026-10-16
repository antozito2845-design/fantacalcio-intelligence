-- FBref is a statistical source only. This table is intentionally separate
-- from player_source_mappings and is never used by reconciliation/sources_count.
CREATE TABLE player_statistical_source_links (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players(id),
    source VARCHAR(30) NOT NULL,
    external_id VARCHAR(150) NOT NULL,
    external_name VARCHAR(150) NOT NULL,
    match_method VARCHAR(40) NOT NULL,
    confidence VARCHAR(10) NOT NULL,
    evidence JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_statistical_link_source_external UNIQUE (source, external_id),
    CONSTRAINT uq_statistical_link_source_player UNIQUE (source, player_id),
    CONSTRAINT ck_statistical_link_source CHECK (source = 'FBREF'),
    CONSTRAINT ck_statistical_link_method CHECK (
        match_method IN ('EXACT_FULL_NAME', 'CROSS_SEASON_EXACT_ANCHOR', 'MANUAL_VERIFIED')
    ),
    CONSTRAINT ck_statistical_link_confidence CHECK (confidence = 'HIGH')
);

CREATE INDEX idx_statistical_links_player
    ON player_statistical_source_links(player_id);

ALTER TABLE player_season_statistics
    ALTER COLUMN club_id DROP NOT NULL;

CREATE TABLE player_season_statistics_clubs (
    id BIGSERIAL PRIMARY KEY,
    statistics_id BIGINT NOT NULL
        REFERENCES player_season_statistics(id) ON DELETE CASCADE,
    club_name VARCHAR(150) NOT NULL,
    normalized_club_name VARCHAR(150) NOT NULL,
    source_order SMALLINT NOT NULL,

    CONSTRAINT uq_statistics_historical_club
        UNIQUE (statistics_id, normalized_club_name),
    CONSTRAINT ck_statistics_club_order CHECK (source_order > 0)
);

CREATE INDEX idx_statistics_clubs_statistics
    ON player_season_statistics_clubs(statistics_id, source_order);
