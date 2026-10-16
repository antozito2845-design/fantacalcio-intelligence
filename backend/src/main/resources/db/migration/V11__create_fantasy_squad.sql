CREATE TABLE fantasy_squads (
    id BIGSERIAL PRIMARY KEY,
    league_configuration_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    initial_budget INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_squad_league
        FOREIGN KEY (league_configuration_id)
        REFERENCES league_configurations(id),

    CONSTRAINT ck_squad_budget
        CHECK (initial_budget BETWEEN 150 AND 1000)
);

CREATE UNIQUE INDEX uq_active_fantasy_squad
    ON fantasy_squads(active)
    WHERE active = TRUE;

CREATE TABLE fantasy_squad_players (
    id BIGSERIAL PRIMARY KEY,
    squad_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    fantasy_role VARCHAR(5) NOT NULL,
    purchase_price INTEGER NOT NULL,
    acquired_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_squad_player_squad
        FOREIGN KEY (squad_id)
        REFERENCES fantasy_squads(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_squad_player_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_squad_player_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_squad_player
        UNIQUE (squad_id, player_id),

    CONSTRAINT ck_squad_player_role
        CHECK (fantasy_role IN ('P', 'D', 'C', 'A')),

    CONSTRAINT ck_squad_player_price
        CHECK (purchase_price >= 1)
);

CREATE INDEX idx_squad_players_squad_role
    ON fantasy_squad_players(squad_id, fantasy_role);

INSERT INTO fantasy_squads (
    league_configuration_id,
    name,
    initial_budget,
    active
)
SELECT
    id,
    'La mia rosa',
    total_budget,
    TRUE
FROM league_configurations
WHERE active = TRUE;