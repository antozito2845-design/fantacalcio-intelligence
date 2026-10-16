CREATE TABLE player_reconciliation_results (
    id BIGSERIAL PRIMARY KEY,
    season_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    normalized_club_name VARCHAR(100) NOT NULL,
    sources_count INTEGER NOT NULL,
    matched_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reconciliation_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT fk_reconciliation_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT uq_reconciliation_player_season
        UNIQUE (player_id, season_id),

    CONSTRAINT ck_reconciliation_sources
        CHECK (sources_count BETWEEN 1 AND 3)
);

CREATE INDEX idx_reconciliation_season
    ON player_reconciliation_results(season_id);