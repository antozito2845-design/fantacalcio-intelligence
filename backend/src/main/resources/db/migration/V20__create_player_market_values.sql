CREATE TABLE player_market_values (
    id BIGSERIAL PRIMARY KEY,

    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,

    source VARCHAR(30) NOT NULL,

    quotation NUMERIC(10,2),
    fantasy_market_value NUMERIC(10,2),

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_market_value_player
        FOREIGN KEY (player_id)
        REFERENCES players(id),

    CONSTRAINT fk_market_value_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id),

    CONSTRAINT uq_player_market_value
        UNIQUE (
            player_id,
            season_id,
            source
        ),

    CONSTRAINT ck_market_value_quotation
        CHECK (
            quotation IS NULL
            OR quotation >= 0
        ),

    CONSTRAINT ck_market_value_fmv
        CHECK (
            fantasy_market_value IS NULL
            OR fantasy_market_value >= 0
        )
);

CREATE INDEX idx_player_market_values_lookup
    ON player_market_values (
        player_id,
        season_id,
        source
    );

CREATE INDEX idx_player_market_values_fmv
    ON player_market_values (
        season_id,
        source,
        fantasy_market_value DESC
    );