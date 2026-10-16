CREATE TABLE auction_price_snapshots (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,

    league_teams INTEGER NOT NULL,
    total_budget INTEGER NOT NULL,
    defense_modifier BOOLEAN NOT NULL,

    fantasy_role VARCHAR(5) NOT NULL,

    minimum_price INTEGER NOT NULL,
    recommended_price INTEGER NOT NULL,
    maximum_price INTEGER NOT NULL,

    budget_percentage NUMERIC(6,3) NOT NULL,
    calculation_score NUMERIC(6,2) NOT NULL,

    calculated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_price_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_price_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT ck_price_teams
        CHECK (league_teams BETWEEN 6 AND 12),

    CONSTRAINT ck_price_budget
        CHECK (total_budget BETWEEN 150 AND 1000),

    CONSTRAINT ck_price_role
        CHECK (fantasy_role IN ('P', 'D', 'C', 'A')),

    CONSTRAINT ck_price_values
        CHECK (
            minimum_price >= 1
            AND recommended_price >= minimum_price
            AND maximum_price >= recommended_price
        )
);

CREATE INDEX idx_price_snapshots_player
    ON auction_price_snapshots(
        player_id,
        season_id,
        calculated_at DESC
    );