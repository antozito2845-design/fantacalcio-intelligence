CREATE TABLE player_value_algorithms (
 version varchar(50) PRIMARY KEY,
 reference_season_id bigint NOT NULL REFERENCES seasons(id),
 status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','SUPERSEDED')),
 model_family varchar(50) NOT NULL CHECK (model_family='RULE_BASED_FANTASY_VALUE'),
 minimum_components smallint NOT NULL CHECK (minimum_components >= 2),
 definition_fingerprint char(64) NOT NULL CHECK (definition_fingerprint ~ '^[0-9a-f]{64}$'),
 created_at timestamptz NOT NULL DEFAULT current_timestamp
);

CREATE TABLE player_value_profiles (
 id bigserial PRIMARY KEY,
 player_id bigint NOT NULL REFERENCES players(id),
 reference_season_id bigint NOT NULL REFERENCES seasons(id),
 algorithm_version varchar(50) NOT NULL REFERENCES player_value_algorithms(version),
 role_code char(1) NOT NULL CHECK (role_code IN ('P','D','C','A')),
 fantasy_quality_score numeric(5,2), usage_score numeric(5,2), production_score numeric(5,2),
 consistency_score numeric(5,2), discipline_score numeric(5,2), goalkeeper_specific_score numeric(5,2),
 player_value_score numeric(5,2), data_confidence_score numeric(5,2) NOT NULL,
 ranking_status varchar(30) NOT NULL CHECK (ranking_status IN ('RANKED','INSUFFICIENT_EVIDENCE')),
 role_rank integer, role_percentile numeric(5,2), component_payload jsonb NOT NULL,
 evidence jsonb NOT NULL, input_fingerprint char(64) NOT NULL,
 generated_at timestamptz NOT NULL DEFAULT current_timestamp,
 UNIQUE(player_id,reference_season_id,algorithm_version),
 CHECK (data_confidence_score BETWEEN 0 AND 100),
 CHECK (input_fingerprint ~ '^[0-9a-f]{64}$'),
 CHECK (jsonb_typeof(component_payload)='object' AND jsonb_typeof(evidence)='object'),
 CHECK ((ranking_status='RANKED' AND player_value_score IS NOT NULL AND role_rank > 0 AND role_percentile BETWEEN 0 AND 100)
     OR (ranking_status='INSUFFICIENT_EVIDENCE' AND player_value_score IS NULL AND role_rank IS NULL AND role_percentile IS NULL)),
 CHECK ((fantasy_quality_score IS NULL OR fantasy_quality_score BETWEEN 0 AND 100)
   AND (usage_score IS NULL OR usage_score BETWEEN 0 AND 100)
   AND (production_score IS NULL OR production_score BETWEEN 0 AND 100)
   AND (consistency_score IS NULL OR consistency_score BETWEEN 0 AND 100)
   AND (discipline_score IS NULL OR discipline_score BETWEEN 0 AND 100)
   AND (goalkeeper_specific_score IS NULL OR goalkeeper_specific_score BETWEEN 0 AND 100)
   AND (player_value_score IS NULL OR player_value_score BETWEEN 0 AND 100))
);
CREATE INDEX idx_player_value_role_rank ON player_value_profiles(reference_season_id,algorithm_version,role_code,role_rank);
