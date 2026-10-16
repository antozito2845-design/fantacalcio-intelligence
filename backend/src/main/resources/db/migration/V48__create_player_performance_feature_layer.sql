-- Immutable, source-aware historical performance features for the current catalog.
-- Raw statistics remain the system of record; this schema contains only derived evidence.

CREATE TABLE player_performance_algorithms (
    version VARCHAR(50) PRIMARY KEY,
    reference_season_id BIGINT NOT NULL REFERENCES seasons(id),
    status VARCHAR(20) NOT NULL,
    model_decision VARCHAR(80) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_performance_algorithm_status CHECK (status IN ('ACTIVE', 'SUPERSEDED')),
    CONSTRAINT ck_performance_model_decision CHECK (
        model_decision IN ('COMPONENT_FEATURES_ONLY', 'OVERALL_SCORE_DEFERRED_TO_BLOCK_50')
    )
);

CREATE TABLE player_performance_feature_definitions (
    algorithm_version VARCHAR(50) NOT NULL REFERENCES player_performance_algorithms(version),
    feature_key VARCHAR(100) NOT NULL,
    semantic_class VARCHAR(50) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    definition TEXT NOT NULL,
    null_policy VARCHAR(40) NOT NULL,
    PRIMARY KEY (algorithm_version, feature_key),
    CONSTRAINT ck_performance_definition_null_policy CHECK (null_policy = 'NULL_STAYS_NULL')
);

CREATE TABLE player_performance_feature_profiles (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players(id),
    reference_season_id BIGINT NOT NULL REFERENCES seasons(id),
    current_club_id BIGINT NOT NULL REFERENCES clubs(id),
    role_code CHAR(1) NOT NULL,
    algorithm_version VARCHAR(50) NOT NULL REFERENCES player_performance_algorithms(version),
    history_seasons_available SMALLINT NOT NULL,
    latest_history_season VARCHAR(9) NOT NULL,
    source_mix VARCHAR(30) NOT NULL,
    small_sample BOOLEAN NOT NULL,
    data_confidence_score NUMERIC(5,2) NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    feature_values JSONB NOT NULL,
    evidence JSONB NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_player_performance_profile_version
        UNIQUE (player_id, reference_season_id, algorithm_version),
    CONSTRAINT ck_player_performance_role CHECK (role_code IN ('P', 'D', 'C', 'A')),
    CONSTRAINT ck_player_performance_history CHECK (history_seasons_available BETWEEN 1 AND 2),
    CONSTRAINT ck_player_performance_source_mix CHECK (
        source_mix IN ('TWO_FANTACALCIO', 'MIXED', 'TWO_INTERNATIONAL', 'ONE_SEASON_ONLY')
    ),
    CONSTRAINT ck_player_performance_confidence CHECK (
        data_confidence_score BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_player_performance_fingerprint CHECK (
        input_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_player_performance_feature_object CHECK (jsonb_typeof(feature_values) = 'object'),
    CONSTRAINT ck_player_performance_evidence_object CHECK (jsonb_typeof(evidence) = 'object'),
    CONSTRAINT ck_player_performance_no_overall_score CHECK (
        NOT (feature_values ? 'historical_performance_score')
        OR feature_values->'historical_performance_score' = 'null'::jsonb
    ),
    CONSTRAINT ck_player_performance_no_league_adjustment CHECK (
        NOT (feature_values ? 'cross_league_adjustment')
        OR feature_values->'cross_league_adjustment' = 'null'::jsonb
    )
);

CREATE INDEX idx_player_performance_profile_player
    ON player_performance_feature_profiles(player_id, reference_season_id);
CREATE INDEX idx_player_performance_profile_role
    ON player_performance_feature_profiles(reference_season_id, algorithm_version, role_code);
