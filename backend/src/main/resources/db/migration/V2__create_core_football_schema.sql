CREATE TABLE seasons (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(9) NOT NULL UNIQUE,
    start_year SMALLINT NOT NULL,
    end_year SMALLINT NOT NULL,
    current_season BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_seasons_years
        CHECK (end_year = start_year + 1)
);

CREATE TABLE clubs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    short_name VARCHAR(30) NOT NULL,
    slug VARCHAR(120) NOT NULL UNIQUE,
    country_code CHAR(2) NOT NULL DEFAULT 'IT',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE players (
    id BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100),
    last_name VARCHAR(100) NOT NULL,
    display_name VARCHAR(150) NOT NULL,
    normalized_name VARCHAR(150) NOT NULL,
    birth_date DATE,
    nationality_code CHAR(2),
    preferred_foot VARCHAR(10),
    height_cm SMALLINT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_players_preferred_foot
        CHECK (
            preferred_foot IS NULL
            OR preferred_foot IN ('RIGHT', 'LEFT', 'BOTH')
        ),

    CONSTRAINT ck_players_height
        CHECK (
            height_cm IS NULL
            OR height_cm BETWEEN 140 AND 220
        )
);

CREATE INDEX idx_players_normalized_name
    ON players(normalized_name);

CREATE TABLE player_registrations (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    club_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    shirt_number SMALLINT,
    joined_at DATE,
    left_at DATE,
    registration_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_registrations_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_registrations_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id),

    CONSTRAINT fk_registrations_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_registration_player_season
        UNIQUE (player_id, season_id),

    CONSTRAINT ck_registration_status
        CHECK (
            registration_status IN (
                'ACTIVE',
                'TRANSFERRED',
                'LOANED',
                'RELEASED'
            )
        )
);

CREATE INDEX idx_registrations_club_season
    ON player_registrations(club_id, season_id);

CREATE TABLE player_roles (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    source VARCHAR(30) NOT NULL,
    fantasy_role VARCHAR(5) NOT NULL,
    tactical_role VARCHAR(50),
    primary_role BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATE,
    valid_to DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_player_roles_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_player_roles_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_player_role_source
        UNIQUE (player_id, season_id, source),

    CONSTRAINT ck_fantasy_role
        CHECK (
            fantasy_role IN ('P', 'D', 'C', 'A')
        ),

    CONSTRAINT ck_role_source
        CHECK (
            source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT',
                'INTERNAL'
            )
        )
);

CREATE INDEX idx_player_roles_season_role
    ON player_roles(season_id, fantasy_role);

CREATE TABLE player_season_statistics (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    club_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    source VARCHAR(30) NOT NULL,

    appearances INTEGER NOT NULL DEFAULT 0,
    starts INTEGER NOT NULL DEFAULT 0,
    minutes_played INTEGER NOT NULL DEFAULT 0,

    average_rating NUMERIC(5,2),
    fantasy_average NUMERIC(5,2),

    goals INTEGER NOT NULL DEFAULT 0,
    assists INTEGER NOT NULL DEFAULT 0,
    penalties_scored INTEGER NOT NULL DEFAULT 0,
    penalties_missed INTEGER NOT NULL DEFAULT 0,

    yellow_cards INTEGER NOT NULL DEFAULT 0,
    red_cards INTEGER NOT NULL DEFAULT 0,
    own_goals INTEGER NOT NULL DEFAULT 0,

    goals_conceded INTEGER NOT NULL DEFAULT 0,
    clean_sheets INTEGER NOT NULL DEFAULT 0,
    penalties_saved INTEGER NOT NULL DEFAULT 0,

    expected_goals NUMERIC(7,3),
    expected_assists NUMERIC(7,3),

    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_statistics_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_statistics_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id),

    CONSTRAINT fk_statistics_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_statistics_player_season_source
        UNIQUE (player_id, season_id, source),

    CONSTRAINT ck_statistics_source
        CHECK (
            source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT',
                'INTERNAL'
            )
        ),

    CONSTRAINT ck_statistics_non_negative
        CHECK (
            appearances >= 0
            AND starts >= 0
            AND minutes_played >= 0
            AND goals >= 0
            AND assists >= 0
        )
);

CREATE INDEX idx_statistics_player_season
    ON player_season_statistics(player_id, season_id);

CREATE TABLE player_source_mappings (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL,
    source VARCHAR(30) NOT NULL,
    external_id VARCHAR(150),
    external_name VARCHAR(150) NOT NULL,
    external_url TEXT,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_source_mapping_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT uq_source_external_player
        UNIQUE (source, external_name),

    CONSTRAINT ck_mapping_source
        CHECK (
            source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT'
            )
        )
);

CREATE INDEX idx_source_mappings_player
    ON player_source_mappings(player_id);

INSERT INTO seasons (
    code,
    start_year,
    end_year,
    current_season
) VALUES
    ('2024/2025', 2024, 2025, FALSE),
    ('2025/2026', 2025, 2026, FALSE),
    ('2026/2027', 2026, 2027, TRUE);