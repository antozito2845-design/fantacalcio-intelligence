CREATE TABLE player_strategic_data_observations (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players(id),
    season_id BIGINT NOT NULL REFERENCES seasons(id),
    source VARCHAR(30) NOT NULL,
    source_record_id VARCHAR(150),
    observed_at TIMESTAMP WITH TIME ZONE,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    evidence_status VARCHAR(20) NOT NULL DEFAULT 'NOT_AVAILABLE',
    confidence_score NUMERIC(5,2),

    real_position_raw VARCHAR(120),
    real_position_normalized VARCHAR(20),
    penalty_hierarchy VARCHAR(20),
    direct_free_kick_hierarchy VARCHAR(20),
    indirect_free_kick_hierarchy VARCHAR(20),
    corner_hierarchy VARCHAR(20),
    set_piece_side VARCHAR(10),
    set_piece_shared BOOLEAN,

    shots INTEGER,
    shots_on_target INTEGER,
    expected_goals NUMERIC(10,4),
    expected_assists NUMERIC(10,4),
    touches_in_box INTEGER,
    key_passes INTEGER,
    shot_creating_actions INTEGER,
    goal_creating_actions INTEGER,
    penalties_attempted INTEGER,
    penalties_scored INTEGER,
    penalties_missed INTEGER,

    aerial_duels INTEGER,
    aerial_duels_won INTEGER,
    headed_shots INTEGER,
    headed_goals INTEGER,
    headed_expected_goals NUMERIC(10,4),
    offensive_box_touches INTEGER,
    set_piece_box_presence NUMERIC(10,4),
    corner_target_frequency NUMERIC(10,4),

    appearances INTEGER,
    starts INTEGER,
    minutes_played INTEGER,
    starter_probability NUMERIC(5,2),
    recent_minutes_trend VARCHAR(30),
    position_usage JSONB,
    evidence JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_strategic_observation UNIQUE (player_id, season_id, source, source_record_id),
    CONSTRAINT ck_strategic_source CHECK (source IN ('FANTACALCIO','GAZZETTA','FANTAMARKT','FBREF','TRANSFERMARKT','MANUAL_VERIFIED')),
    CONSTRAINT ck_strategic_evidence CHECK (evidence_status IN ('AVAILABLE','PARTIAL','MISSING','UNKNOWN','NOT_AVAILABLE')),
    CONSTRAINT ck_strategic_confidence CHECK (confidence_score IS NULL OR confidence_score BETWEEN 0 AND 100),
    CONSTRAINT ck_strategic_position CHECK (real_position_normalized IS NULL OR real_position_normalized IN ('GK','CB','RCB','LCB','RB','LB','RWB','LWB','DM','CM','RCM','LCM','AM','RAM','LAM','RW','LW','SS','CF','ST','UTILITY','UNKNOWN')),
    CONSTRAINT ck_penalty_hierarchy CHECK (penalty_hierarchy IS NULL OR penalty_hierarchy IN ('PRIMARY','SECONDARY','EMERGENCY','UNKNOWN')),
    CONSTRAINT ck_direct_fk_hierarchy CHECK (direct_free_kick_hierarchy IS NULL OR direct_free_kick_hierarchy IN ('PRIMARY','SECONDARY','UNKNOWN')),
    CONSTRAINT ck_indirect_fk_hierarchy CHECK (indirect_free_kick_hierarchy IS NULL OR indirect_free_kick_hierarchy IN ('PRIMARY','SECONDARY','UNKNOWN')),
    CONSTRAINT ck_corner_hierarchy CHECK (corner_hierarchy IS NULL OR corner_hierarchy IN ('PRIMARY','SECONDARY','UNKNOWN')),
    CONSTRAINT ck_set_piece_side CHECK (set_piece_side IS NULL OR set_piece_side IN ('LEFT','RIGHT','BOTH')),
    CONSTRAINT ck_strategic_counts CHECK (
        (shots IS NULL OR shots >= 0) AND (shots_on_target IS NULL OR shots_on_target >= 0)
        AND (touches_in_box IS NULL OR touches_in_box >= 0) AND (key_passes IS NULL OR key_passes >= 0)
        AND (shot_creating_actions IS NULL OR shot_creating_actions >= 0) AND (goal_creating_actions IS NULL OR goal_creating_actions >= 0)
        AND (penalties_attempted IS NULL OR penalties_attempted >= 0) AND (penalties_scored IS NULL OR penalties_scored >= 0)
        AND (penalties_missed IS NULL OR penalties_missed >= 0) AND (aerial_duels IS NULL OR aerial_duels >= 0)
        AND (aerial_duels_won IS NULL OR aerial_duels_won >= 0) AND (headed_shots IS NULL OR headed_shots >= 0)
        AND (headed_goals IS NULL OR headed_goals >= 0) AND (offensive_box_touches IS NULL OR offensive_box_touches >= 0)
        AND (appearances IS NULL OR appearances >= 0) AND (starts IS NULL OR starts >= 0)
        AND (minutes_played IS NULL OR minutes_played >= 0)
    ),
    CONSTRAINT ck_strategic_relations CHECK (
        (shots_on_target IS NULL OR shots IS NULL OR shots_on_target <= shots)
        AND (aerial_duels_won IS NULL OR aerial_duels IS NULL OR aerial_duels_won <= aerial_duels)
        AND (starts IS NULL OR appearances IS NULL OR starts <= appearances)
    ),
    CONSTRAINT ck_strategic_non_negative_decimals CHECK (
        (expected_goals IS NULL OR expected_goals >= 0) AND (expected_assists IS NULL OR expected_assists >= 0)
        AND (headed_expected_goals IS NULL OR headed_expected_goals >= 0)
        AND (set_piece_box_presence IS NULL OR set_piece_box_presence >= 0)
        AND (corner_target_frequency IS NULL OR corner_target_frequency >= 0)
        AND (starter_probability IS NULL OR starter_probability BETWEEN 0 AND 100)
    ),
    CONSTRAINT ck_strategic_evidence_object CHECK (jsonb_typeof(evidence) = 'object'),
    CONSTRAINT ck_strategic_position_usage_object CHECK (position_usage IS NULL OR jsonb_typeof(position_usage) = 'object')
);

CREATE UNIQUE INDEX uq_strategic_observation_without_record
    ON player_strategic_data_observations(player_id, season_id, source)
    WHERE source_record_id IS NULL;

CREATE INDEX idx_strategic_observation_lookup
    ON player_strategic_data_observations(player_id, season_id, source);

COMMENT ON TABLE player_strategic_data_observations IS
    'B55 source observations. Conflicting sources coexist; NULL means not supplied and must not be inferred.';
