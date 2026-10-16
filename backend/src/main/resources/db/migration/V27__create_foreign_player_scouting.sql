CREATE TABLE foreign_player_scouting_reports (
    id BIGSERIAL PRIMARY KEY,

    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,

    previous_club VARCHAR(150) NOT NULL,
    previous_league VARCHAR(150) NOT NULL,
    previous_country_code VARCHAR(2) NOT NULL,

    transfer_type VARCHAR(30) NOT NULL,

    adaptation_score NUMERIC(5,2) NOT NULL,
    potential_score NUMERIC(5,2) NOT NULL,
    risk_score NUMERIC(5,2) NOT NULL,
    confidence_score NUMERIC(5,2) NOT NULL,

    recommendation VARCHAR(30) NOT NULL,
    scouting_summary TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_foreign_scouting_player
        FOREIGN KEY (player_id)
        REFERENCES players(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_foreign_scouting_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_foreign_scouting_player_season
        UNIQUE (player_id, season_id),

    CONSTRAINT ck_foreign_scouting_country
        CHECK (
            LENGTH(previous_country_code) = 2
            AND previous_country_code <> 'IT'
        ),

    CONSTRAINT ck_foreign_scouting_transfer_type
        CHECK (
            transfer_type IN (
                'PERMANENT',
                'LOAN',
                'FREE_AGENT',
                'RETURN_FROM_LOAN'
            )
        ),

    CONSTRAINT ck_foreign_scouting_adaptation
        CHECK (adaptation_score BETWEEN 0 AND 100),

    CONSTRAINT ck_foreign_scouting_potential
        CHECK (potential_score BETWEEN 0 AND 100),

    CONSTRAINT ck_foreign_scouting_risk
        CHECK (risk_score BETWEEN 0 AND 100),

    CONSTRAINT ck_foreign_scouting_confidence
        CHECK (confidence_score BETWEEN 0 AND 100),

    CONSTRAINT ck_foreign_scouting_recommendation
        CHECK (
            recommendation IN (
                'HIGH_PRIORITY',
                'RECOMMENDED',
                'MONITOR',
                'HIGH_RISK'
            )
        )
);

CREATE INDEX idx_foreign_scouting_season_recommendation
    ON foreign_player_scouting_reports(
        season_id,
        recommendation,
        potential_score DESC
    );

CREATE INDEX idx_foreign_scouting_player
    ON foreign_player_scouting_reports(player_id);