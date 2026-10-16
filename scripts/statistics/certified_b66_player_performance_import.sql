\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE stage_b66_performance_profiles (
    player_id text,
    player_name text,
    role_code text,
    current_club_id text,
    current_club text,
    reference_season text,
    algorithm_version text,
    history_seasons_available text,
    latest_history_season text,
    source_mix text,
    small_sample_flag text,
    data_confidence_score text,
    input_fingerprint text,
    feature_values_json text,
    evidence_json text
) ON COMMIT DROP;

\copy stage_b66_performance_profiles FROM 'data/statistics/features/b66-player-performance-import.csv' WITH (FORMAT csv, HEADER true)

DO $$
DECLARE
    operational_count integer;
    staged_count integer;
BEGIN

    SELECT COUNT(*)
    INTO operational_count
    FROM player_registrations r
    JOIN seasons se
        ON se.id = r.season_id
    WHERE se.code = '2026/2027'
      AND r.registration_status = 'ACTIVE';

    SELECT COUNT(*)
    INTO staged_count
    FROM stage_b66_performance_profiles;

    IF staged_count <> operational_count THEN
        RAISE EXCEPTION
            'B66 staged population % does not match operational population %',
            staged_count,
            operational_count;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles
        GROUP BY player_id
        HAVING COUNT(*) <> 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate staged B66 player';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles
        WHERE algorithm_version <> 'B66_FEATURES_V1'
           OR reference_season <> '2026/2027'
           OR role_code NOT IN ('P','D','C','A')
           OR history_seasons_available::integer NOT BETWEEN 0 AND 2
           OR source_mix NOT IN (
                'TWO_FANTACALCIO',
                'MIXED',
                'TWO_INTERNATIONAL',
                'ONE_SEASON_ONLY',
                'NO_HISTORY'
           )
           OR small_sample_flag NOT IN ('YES','NO')
           OR data_confidence_score::numeric NOT BETWEEN 0 AND 100
           OR input_fingerprint !~ '^[0-9a-f]{64}$'
           OR jsonb_typeof(feature_values_json::jsonb) <> 'object'
           OR jsonb_typeof(evidence_json::jsonb) <> 'object'
    ) THEN
        RAISE EXCEPTION
            'Invalid B66 staged performance contract';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles
        WHERE source_mix = 'NO_HISTORY'
          AND (
                history_seasons_available::integer <> 0
                OR NULLIF(latest_history_season, '') IS NOT NULL
                OR data_confidence_score::numeric <> 0
                OR small_sample_flag <> 'YES'
          )
    ) THEN
        RAISE EXCEPTION
            'Invalid B66 NO_HISTORY contract';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles s
        CROSS JOIN LATERAL jsonb_each(
            s.feature_values_json::jsonb
        ) feature
        WHERE s.source_mix = 'NO_HISTORY'
          AND feature.value <> 'null'::jsonb
    ) THEN
        RAISE EXCEPTION
            'NO_HISTORY profile contains invented feature values';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles
        WHERE source_mix <> 'NO_HISTORY'
          AND (
                history_seasons_available::integer NOT BETWEEN 1 AND 2
                OR NULLIF(latest_history_season, '') IS NULL
          )
    ) THEN
        RAISE EXCEPTION
            'Historical B66 profile has inconsistent season metadata';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles
        WHERE NULLIF(latest_history_season, '') IS NOT NULL
          AND latest_history_season NOT IN (
                '2024/2025',
                '2025/2026'
          )
    ) THEN
        RAISE EXCEPTION
            'Invalid B66 latest historical season';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles
        WHERE feature_values_json::jsonb
                -> 'historical_performance_score'
                <> 'null'::jsonb
           OR feature_values_json::jsonb
                -> 'cross_league_adjustment'
                <> 'null'::jsonb
    ) THEN
        RAISE EXCEPTION
            'B66 deferred features must remain NULL';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles s

        LEFT JOIN seasons se
            ON se.code = s.reference_season

        LEFT JOIN players p
            ON p.id = s.player_id::bigint

        LEFT JOIN clubs c
            ON c.id = s.current_club_id::bigint

        LEFT JOIN player_registrations r
            ON r.player_id = p.id
           AND r.season_id = se.id

        LEFT JOIN player_roles pr
            ON pr.player_id = p.id
           AND pr.season_id = se.id
           AND pr.source = 'FANTACALCIO'

        WHERE se.id IS NULL
           OR p.id IS NULL
           OR c.id IS NULL
           OR r.registration_status <> 'ACTIVE'
           OR r.club_id <> c.id
           OR pr.fantasy_role <> s.role_code
    ) THEN
        RAISE EXCEPTION
            'B66 current catalog identity/club/role gate failed';
    END IF;

    IF EXISTS (
        SELECT r.player_id
        FROM player_registrations r

        JOIN seasons se
            ON se.id = r.season_id

        WHERE se.code = '2026/2027'
          AND r.registration_status = 'ACTIVE'

        EXCEPT

        SELECT player_id::bigint
        FROM stage_b66_performance_profiles
    ) THEN
        RAISE EXCEPTION
            'Operational player missing from B66 stage';
    END IF;

    IF EXISTS (
        SELECT player_id::bigint
        FROM stage_b66_performance_profiles

        EXCEPT

        SELECT r.player_id
        FROM player_registrations r

        JOIN seasons se
            ON se.id = r.season_id

        WHERE se.code = '2026/2027'
          AND r.registration_status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION
            'B66 stage contains non-operational player';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM player_performance_algorithms a
        WHERE a.version = 'B66_FEATURES_V1'
          AND (
                a.reference_season_id <>
                    (
                        SELECT id
                        FROM seasons
                        WHERE code = '2026/2027'
                    )
                OR a.model_decision <>
                    'COMPONENT_FEATURES_ONLY'
          )
    ) THEN
        RAISE EXCEPTION
            'B66 algorithm version conflict';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_performance_profiles s

        JOIN seasons se
            ON se.code = s.reference_season

        JOIN player_performance_feature_profiles p
            ON p.player_id = s.player_id::bigint
           AND p.reference_season_id = se.id
           AND p.algorithm_version = s.algorithm_version

        WHERE (
            p.current_club_id,
            p.role_code,
            p.history_seasons_available,
            p.latest_history_season,
            p.source_mix,
            p.small_sample,
            p.data_confidence_score,
            p.input_fingerprint,
            p.feature_values,
            p.evidence
        )
        IS DISTINCT FROM
        (
            s.current_club_id::bigint,
            s.role_code::char(1),
            s.history_seasons_available::smallint,
            NULLIF(s.latest_history_season, ''),
            s.source_mix,
            (s.small_sample_flag = 'YES'),
            s.data_confidence_score::numeric,
            s.input_fingerprint::char(64),
            s.feature_values_json::jsonb,
            s.evidence_json::jsonb
        )
    ) THEN
        RAISE EXCEPTION
            'Immutable B66 performance profile version conflict';
    END IF;

END
$$;

\if :preflight

ROLLBACK;

\echo 'B66 PERFORMANCE PREFLIGHT: PASS (NO PERSISTENT DML)'

\quit

\endif


INSERT INTO player_performance_algorithms (
    version,
    reference_season_id,
    status,
    model_decision
)
SELECT
    'B66_FEATURES_V1',
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
    'B66_FEATURES_V1',
    feature_key,
    semantic_class,
    unit,
    definition,
    null_policy
FROM player_performance_feature_definitions
WHERE algorithm_version = 'B49_FEATURES_V1'
ON CONFLICT (
    algorithm_version,
    feature_key
) DO NOTHING;


DO $$
BEGIN

    IF (
        SELECT COUNT(*)
        FROM player_performance_feature_definitions
        WHERE algorithm_version = 'B66_FEATURES_V1'
    ) <> 19 THEN
        RAISE EXCEPTION
            'B66 feature definition count != 19';
    END IF;

END
$$;


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
    s.role_code,
    s.algorithm_version,
    s.history_seasons_available::smallint,
    NULLIF(
        s.latest_history_season,
        ''
    ),
    s.source_mix,
    s.small_sample_flag = 'YES',
    s.data_confidence_score::numeric,
    s.input_fingerprint,
    s.feature_values_json::jsonb,
    s.evidence_json::jsonb

FROM stage_b66_performance_profiles s

JOIN seasons se
    ON se.code = s.reference_season

WHERE NOT EXISTS (
    SELECT 1
    FROM player_performance_feature_profiles p
    WHERE p.player_id = s.player_id::bigint
      AND p.reference_season_id = se.id
      AND p.algorithm_version = s.algorithm_version
);


DO $$
DECLARE
    operational_count integer;
    final_count integer;
    no_history_count integer;
BEGIN

    SELECT COUNT(*)
    INTO operational_count
    FROM player_registrations r
    JOIN seasons se
        ON se.id = r.season_id
    WHERE se.code = '2026/2027'
      AND r.registration_status = 'ACTIVE';

    SELECT COUNT(*)
    INTO final_count
    FROM player_performance_feature_profiles
    WHERE algorithm_version = 'B66_FEATURES_V1';

    IF final_count <> operational_count THEN
        RAISE EXCEPTION
            'Final B66 profile count % != operational count %',
            final_count,
            operational_count;
    END IF;

    SELECT COUNT(*)
    INTO no_history_count
    FROM player_performance_feature_profiles
    WHERE algorithm_version = 'B66_FEATURES_V1'
      AND source_mix = 'NO_HISTORY';

    IF EXISTS (
        SELECT 1
        FROM player_performance_feature_profiles
        WHERE algorithm_version = 'B66_FEATURES_V1'
          AND (
                history_seasons_available NOT BETWEEN 0 AND 2
                OR data_confidence_score NOT BETWEEN 0 AND 100
                OR feature_values
                    -> 'historical_performance_score'
                    <> 'null'::jsonb
                OR feature_values
                    -> 'cross_league_adjustment'
                    <> 'null'::jsonb
          )
    ) THEN
        RAISE EXCEPTION
            'Final B66 feature invariant failed';
    END IF;

    RAISE NOTICE
        'B66 final profiles=%, NO_HISTORY=%',
        final_count,
        no_history_count;

END
$$;

COMMIT;

\echo 'B66 PERFORMANCE IMPORT: PASS (COMMIT)'