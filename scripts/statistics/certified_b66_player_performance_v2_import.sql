\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE stage_b66_v2 (
    player_id text,
    player_name text,
    role_code text,
    current_club_id text,
    current_club text,
    reference_season text,
    history_seasons_available text,
    latest_history_season text,
    source_mix text,
    small_sample_flag text,
    data_confidence_score text,
    algorithm_version text,
    input_fingerprint text,
    feature_values_json text,
    evidence_json text
) ON COMMIT DROP;

\copy stage_b66_v2 FROM 'data/statistics/features/b66-player-performance-import-v2.csv' WITH (FORMAT csv, HEADER true)

DO $$
BEGIN
    IF (SELECT count(*) FROM stage_b66_v2) <> 498 THEN
        RAISE EXCEPTION 'B66 V2 profiles != 498';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_v2
        WHERE algorithm_version <> 'B66_FEATURES_V2'
           OR reference_season <> '2026/2027'
           OR role_code NOT IN ('P','D','C','A')
           OR history_seasons_available::int NOT BETWEEN 0 AND 2
           OR source_mix NOT IN (
                'TWO_FANTACALCIO',
                'MIXED',
                'TWO_INTERNATIONAL',
                'ONE_SEASON_ONLY',
                'NO_HISTORY'
           )
           OR data_confidence_score::numeric NOT BETWEEN 0 AND 100
           OR input_fingerprint !~ '^[0-9a-f]{64}$'
           OR jsonb_typeof(feature_values_json::jsonb) <> 'object'
           OR jsonb_typeof(evidence_json::jsonb) <> 'object'
    ) THEN
        RAISE EXCEPTION 'Invalid B66 V2 contract';
    END IF;

    IF (
        SELECT count(*)
        FROM stage_b66_v2
        WHERE source_mix = 'NO_HISTORY'
    ) <> 1 THEN
        RAISE EXCEPTION 'B66 V2 NO_HISTORY count != 1';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_v2
        WHERE source_mix = 'NO_HISTORY'
          AND (
              player_id::bigint <> 1039
              OR history_seasons_available::int <> 0
              OR NULLIF(latest_history_season, '') IS NOT NULL
              OR data_confidence_score::numeric <> 0
          )
    ) THEN
        RAISE EXCEPTION 'Penev NO_HISTORY contract invalid';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_v2 s,
             LATERAL jsonb_each(s.feature_values_json::jsonb) f
        WHERE s.source_mix = 'NO_HISTORY'
          AND f.value <> 'null'::jsonb
    ) THEN
        RAISE EXCEPTION 'Invented NO_HISTORY feature';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_v2 s
        LEFT JOIN seasons se
          ON se.code = '2026/2027'
        LEFT JOIN player_registrations r
          ON r.player_id = s.player_id::bigint
         AND r.season_id = se.id
         AND r.registration_status = 'ACTIVE'
        WHERE r.player_id IS NULL
           OR r.club_id <> s.current_club_id::bigint
    ) THEN
        RAISE EXCEPTION 'B66 V2 current registration gate failed';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_v2 s
        LEFT JOIN player_reconciliation_results rr
          ON rr.player_id = s.player_id::bigint
        LEFT JOIN seasons se
          ON se.id = rr.season_id
         AND se.code = '2026/2027'
        WHERE se.id IS NULL
    ) THEN
        RAISE EXCEPTION 'B66 V2 operational identity gate failed';
    END IF;
END
$$;

UPDATE player_performance_algorithms
SET status = 'SUPERSEDED'
WHERE version = 'B66_FEATURES_V1'
  AND status = 'ACTIVE';

INSERT INTO player_performance_algorithms (
    version,
    reference_season_id,
    status,
    model_decision
)
SELECT
    'B66_FEATURES_V2',
    id,
    'ACTIVE',
    'COMPONENT_FEATURES_ONLY'
FROM seasons
WHERE code = '2026/2027'
ON CONFLICT (version) DO NOTHING;

INSERT INTO player_performance_feature_definitions (
    algorithm_version,
    feature_key,
    semantic_class,
    unit,
    definition,
    null_policy
)
SELECT
    'B66_FEATURES_V2',
    feature_key,
    semantic_class,
    unit,
    definition,
    null_policy
FROM player_performance_feature_definitions
WHERE algorithm_version = 'B66_FEATURES_V1'
ON CONFLICT (
    algorithm_version,
    feature_key
) DO NOTHING;

INSERT INTO player_performance_feature_profiles (
    player_id,
    reference_season_id,
    current_club_id,
    role_code,
    algorithm_version,
    history_seasons_available,
    latest_history_season,
    source_mix,
    small_sample,
    data_confidence_score,
    input_fingerprint,
    feature_values,
    evidence
)
SELECT
    s.player_id::bigint,
    se.id,
    s.current_club_id::bigint,
    s.role_code::char(1),
    'B66_FEATURES_V2',
    s.history_seasons_available::smallint,
    NULLIF(s.latest_history_season, ''),
    s.source_mix,
    CASE
        WHEN upper(s.small_sample_flag) IN (
            'YES','TRUE','1'
        ) THEN TRUE
        ELSE FALSE
    END,
    s.data_confidence_score::numeric,
    s.input_fingerprint,
    s.feature_values_json::jsonb,
    s.evidence_json::jsonb
FROM stage_b66_v2 s
JOIN seasons se
  ON se.code = '2026/2027'
WHERE NOT EXISTS (
    SELECT 1
    FROM player_performance_feature_profiles p
    WHERE p.player_id = s.player_id::bigint
      AND p.reference_season_id = se.id
      AND p.algorithm_version = 'B66_FEATURES_V2'
);

DO $$
BEGIN
    IF (
        SELECT count(*)
        FROM player_performance_feature_profiles
        WHERE algorithm_version = 'B66_FEATURES_V2'
    ) <> 498 THEN
        RAISE EXCEPTION 'Persisted B66 V2 profiles != 498';
    END IF;

    IF (
        SELECT count(*)
        FROM player_performance_feature_profiles
        WHERE algorithm_version = 'B66_FEATURES_V2'
          AND source_mix = 'NO_HISTORY'
    ) <> 1 THEN
        RAISE EXCEPTION 'Persisted B66 V2 NO_HISTORY != 1';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM player_performance_feature_profiles
        WHERE algorithm_version = 'B66_FEATURES_V2'
          AND player_id = 1039
          AND source_mix = 'NO_HISTORY'
          AND history_seasons_available = 0
    ) THEN
        RAISE EXCEPTION 'Penev missing as sole NO_HISTORY';
    END IF;

    IF (
        SELECT count(*)
        FROM player_performance_feature_definitions
        WHERE algorithm_version = 'B66_FEATURES_V2'
    ) <> 19 THEN
        RAISE EXCEPTION 'B66 V2 definitions != 19';
    END IF;
END
$$;

COMMIT;

\echo 'B66 PERFORMANCE V2 IMPORT: PASS'