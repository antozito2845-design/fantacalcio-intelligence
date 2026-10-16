CREATE TABLE league_configurations (
    id BIGSERIAL PRIMARY KEY,

    name VARCHAR(100) NOT NULL,
    season_id BIGINT NOT NULL,

    teams_count INTEGER NOT NULL DEFAULT 8,
    total_budget INTEGER NOT NULL DEFAULT 1000,

    goalkeepers_count INTEGER NOT NULL DEFAULT 3,
    defenders_count INTEGER NOT NULL DEFAULT 8,
    midfielders_count INTEGER NOT NULL DEFAULT 8,
    forwards_count INTEGER NOT NULL DEFAULT 6,

    defense_modifier BOOLEAN NOT NULL DEFAULT TRUE,
    captain_bonus BOOLEAN NOT NULL DEFAULT FALSE,

    vote_source VARCHAR(30) NOT NULL DEFAULT 'FANTACALCIO',
    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_league_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT ck_league_teams
        CHECK (teams_count BETWEEN 6 AND 12),

    CONSTRAINT ck_league_budget
        CHECK (total_budget BETWEEN 150 AND 1000),

    CONSTRAINT ck_league_goalkeepers
        CHECK (goalkeepers_count BETWEEN 1 AND 5),

    CONSTRAINT ck_league_defenders
        CHECK (defenders_count BETWEEN 5 AND 12),

    CONSTRAINT ck_league_midfielders
        CHECK (midfielders_count BETWEEN 5 AND 12),

    CONSTRAINT ck_league_forwards
        CHECK (forwards_count BETWEEN 3 AND 10),

    CONSTRAINT ck_league_vote_source
        CHECK (
            vote_source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT',
                'AVERAGE'
            )
        )
);

CREATE UNIQUE INDEX uq_active_league_configuration
    ON league_configurations(active)
    WHERE active = TRUE;

INSERT INTO league_configurations (
    name,
    season_id,
    teams_count,
    total_budget,
    goalkeepers_count,
    defenders_count,
    midfielders_count,
    forwards_count,
    defense_modifier,
    captain_bonus,
    vote_source,
    active
)
SELECT
    'Lega principale',
    id,
    8,
    1000,
    3,
    8,
    8,
    6,
    TRUE,
    FALSE,
    'AVERAGE',
    TRUE
FROM seasons
WHERE code = '2026/2027';