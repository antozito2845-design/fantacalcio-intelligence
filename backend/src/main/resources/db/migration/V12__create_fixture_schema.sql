CREATE TABLE club_strength_ratings (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,

    overall_rating NUMERIC(5,2) NOT NULL,
    attacking_rating NUMERIC(5,2) NOT NULL,
    defensive_rating NUMERIC(5,2) NOT NULL,
    home_rating NUMERIC(5,2) NOT NULL,
    away_rating NUMERIC(5,2) NOT NULL,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_strength_club
        FOREIGN KEY (club_id)
        REFERENCES clubs(id),

    CONSTRAINT fk_strength_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_strength_club_season
        UNIQUE (club_id, season_id),

    CONSTRAINT ck_strength_values
        CHECK (
            overall_rating BETWEEN 0 AND 100
            AND attacking_rating BETWEEN 0 AND 100
            AND defensive_rating BETWEEN 0 AND 100
            AND home_rating BETWEEN 0 AND 100
            AND away_rating BETWEEN 0 AND 100
        )
);

CREATE TABLE fixtures (
    id BIGSERIAL PRIMARY KEY,
    season_id BIGINT NOT NULL,
    matchday INTEGER NOT NULL,

    home_club_id BIGINT NOT NULL,
    away_club_id BIGINT NOT NULL,

    kickoff_at TIMESTAMP WITH TIME ZONE,

    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',

    home_goals INTEGER,
    away_goals INTEGER,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_fixture_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT fk_fixture_home_club
        FOREIGN KEY (home_club_id)
        REFERENCES clubs(id),

    CONSTRAINT fk_fixture_away_club
        FOREIGN KEY (away_club_id)
        REFERENCES clubs(id),

    CONSTRAINT uq_fixture_match
        UNIQUE (
            season_id,
            matchday,
            home_club_id,
            away_club_id
        ),

    CONSTRAINT ck_fixture_matchday
        CHECK (matchday BETWEEN 1 AND 38),

    CONSTRAINT ck_fixture_different_clubs
        CHECK (home_club_id <> away_club_id),

    CONSTRAINT ck_fixture_status
        CHECK (
            status IN (
                'SCHEDULED',
                'LIVE',
                'FINISHED',
                'POSTPONED',
                'CANCELLED'
            )
        )
);

CREATE INDEX idx_fixtures_season_matchday
    ON fixtures(season_id, matchday);

CREATE INDEX idx_fixtures_home_club
    ON fixtures(home_club_id, matchday);

CREATE INDEX idx_fixtures_away_club
    ON fixtures(away_club_id, matchday);