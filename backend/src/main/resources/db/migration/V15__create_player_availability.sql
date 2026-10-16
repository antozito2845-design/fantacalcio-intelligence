CREATE TABLE player_availabilities (
    id BIGSERIAL PRIMARY KEY,

    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    matchday INTEGER NOT NULL,

    status VARCHAR(20) NOT NULL,
    starting_probability NUMERIC(5,2),
    availability_probability NUMERIC(5,2),

    injury_description VARCHAR(255),
    expected_return_date DATE,
    source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_availability_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_availability_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_player_availability
        UNIQUE (player_id, season_id, matchday),

    CONSTRAINT ck_availability_matchday
        CHECK (matchday BETWEEN 1 AND 38),

    CONSTRAINT ck_availability_status
        CHECK (
            status IN (
                'AVAILABLE',
                'PROBABLE',
                'DOUBTFUL',
                'INJURED',
                'SUSPENDED',
                'UNAVAILABLE'
            )
        ),

    CONSTRAINT ck_starting_probability
        CHECK (
            starting_probability IS NULL
            OR starting_probability BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_availability_probability
        CHECK (
            availability_probability IS NULL
            OR availability_probability BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_player_availability_matchday
    ON player_availabilities(season_id, matchday, status);