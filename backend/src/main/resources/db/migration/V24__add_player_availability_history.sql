CREATE TABLE player_availability_history (
    id BIGSERIAL PRIMARY KEY,

    availability_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    matchday INTEGER NOT NULL,

    status VARCHAR(20) NOT NULL,
    starting_probability NUMERIC(5,2),
    availability_probability NUMERIC(5,2),

    injury_description VARCHAR(255),
    expected_return_date DATE,
    source VARCHAR(30) NOT NULL,

    changed_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_availability_history_availability
        FOREIGN KEY (availability_id)
        REFERENCES player_availabilities(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_availability_history_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_availability_history_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT ck_availability_history_matchday
        CHECK (matchday BETWEEN 1 AND 38),

    CONSTRAINT ck_availability_history_status
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

    CONSTRAINT ck_availability_history_start_probability
        CHECK (
            starting_probability IS NULL
            OR starting_probability BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_availability_history_probability
        CHECK (
            availability_probability IS NULL
            OR availability_probability BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_availability_history_player
    ON player_availability_history(
        player_id,
        season_id,
        matchday,
        changed_at DESC
    );

INSERT INTO player_availability_history (
    availability_id,
    player_id,
    season_id,
    matchday,
    status,
    starting_probability,
    availability_probability,
    injury_description,
    expected_return_date,
    source,
    changed_at
)
SELECT
    id,
    player_id,
    season_id,
    matchday,
    status,
    starting_probability,
    availability_probability,
    injury_description,
    expected_return_date,
    source,
    updated_at
FROM player_availabilities;