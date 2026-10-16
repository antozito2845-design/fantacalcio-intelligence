CREATE TABLE coaches (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL UNIQUE,
    nationality_code VARCHAR(2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE club_season_tactics (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    coach_id BIGINT,
    primary_formation VARCHAR(20),
    secondary_formation VARCHAR(20),
    playing_style VARCHAR(100),
    defensive_line VARCHAR(30),
    pressing_level VARCHAR(30),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tactics_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id),

    CONSTRAINT fk_tactics_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT fk_tactics_coach
        FOREIGN KEY (coach_id)
        REFERENCES coaches(id),

    CONSTRAINT uq_club_tactics_season
        UNIQUE (club_id, season_id)
);

CREATE TABLE player_tactical_profiles (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,

    real_position VARCHAR(80),
    secondary_position VARCHAR(80),
    tactical_role VARCHAR(80),

    average_position_height NUMERIC(5,2),
    attacking_involvement NUMERIC(5,2),
    defensive_involvement NUMERIC(5,2),

    set_piece_taker BOOLEAN NOT NULL DEFAULT FALSE,
    penalty_taker BOOLEAN NOT NULL DEFAULT FALSE,
    corner_taker BOOLEAN NOT NULL DEFAULT FALSE,
    free_kick_taker BOOLEAN NOT NULL DEFAULT FALSE,

    projected_starter BOOLEAN NOT NULL DEFAULT FALSE,
    starter_probability NUMERIC(5,2),

    competition_level VARCHAR(30),
    tactical_notes TEXT,

    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_tactical_profile_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_tactical_profile_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_tactical_profile
        UNIQUE (player_id, season_id),

    CONSTRAINT ck_starter_probability
        CHECK (
            starter_probability IS NULL
            OR starter_probability BETWEEN 0 AND 100
        )
);

CREATE TABLE player_evaluations (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,

    projected_fantasy_average NUMERIC(5,2),
    projected_appearances INTEGER,
    projected_starts INTEGER,
    projected_goals INTEGER,
    projected_assists INTEGER,

    reliability_score NUMERIC(5,2),
    potential_score NUMERIC(5,2),
    risk_score NUMERIC(5,2),
    value_score NUMERIC(5,2),

    evaluation_summary TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_evaluation_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_evaluation_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_player_evaluation
        UNIQUE (player_id, season_id)
);

CREATE INDEX idx_statistics_player_source
    ON player_season_statistics(player_id, source);