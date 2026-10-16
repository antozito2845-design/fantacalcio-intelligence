CREATE TABLE player_development_assessments (
    id BIGSERIAL PRIMARY KEY,

    player_id BIGINT NOT NULL,
    target_season_id BIGINT NOT NULL,

    older_season_id BIGINT,
    latest_season_id BIGINT,

    trend VARCHAR(30) NOT NULL,

    growth_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    decline_risk_score NUMERIC(5,2) NOT NULL DEFAULT 0,
    confidence_score NUMERIC(5,2) NOT NULL DEFAULT 0,

    appearances_change NUMERIC(8,2),
    starts_change NUMERIC(8,2),
    minutes_change NUMERIC(8,2),
    fantasy_average_change NUMERIC(8,2),
    goals_change NUMERIC(8,2),
    assists_change NUMERIC(8,2),
    expected_goals_change NUMERIC(8,3),
    expected_assists_change NUMERIC(8,3),

    analysis_summary TEXT NOT NULL,

    generated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_development_player
        FOREIGN KEY (player_id)
        REFERENCES players(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_development_target_season
        FOREIGN KEY (target_season_id)
        REFERENCES seasons(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_development_older_season
        FOREIGN KEY (older_season_id)
        REFERENCES seasons(id),

    CONSTRAINT fk_development_latest_season
        FOREIGN KEY (latest_season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_development_player_target
        UNIQUE (player_id, target_season_id),

    CONSTRAINT ck_development_trend
        CHECK (
            trend IN (
                'GROWING',
                'STABLE',
                'DECLINING',
                'INSUFFICIENT_DATA'
            )
        ),

    CONSTRAINT ck_development_growth_score
        CHECK (
            growth_score BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_development_decline_score
        CHECK (
            decline_risk_score BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_development_confidence_score
        CHECK (
            confidence_score BETWEEN 0 AND 100
        )
);

CREATE INDEX idx_development_target_trend
    ON player_development_assessments(
        target_season_id,
        trend
    );

CREATE INDEX idx_development_player
    ON player_development_assessments(player_id);