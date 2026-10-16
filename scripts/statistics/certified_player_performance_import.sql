\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE stage_performance_profiles (
 player_id text,player_name text,role_code text,current_club_id text,current_club text,
 reference_season text,algorithm_version text,history_seasons_available text,
 latest_history_season text,source_mix text,small_sample_flag text,data_confidence_score text,
 input_fingerprint text,feature_values_json text,evidence_json text
) ON COMMIT DROP;
\if :sandbox_projection
\copy stage_performance_profiles FROM 'data/statistics/features/player-performance-import-sandbox.csv' WITH (FORMAT csv,HEADER true)
\else
\copy stage_performance_profiles FROM 'data/statistics/features/player-performance-import.csv' WITH (FORMAT csv,HEADER true)
\endif

CREATE TEMP TABLE stage_performance_definitions (
 algorithm_version text,feature_key text,semantic_class text,unit text,definition text,null_policy text
) ON COMMIT DROP;
\copy stage_performance_definitions FROM 'data/statistics/features/player-performance-feature-definitions.csv' WITH (FORMAT csv,HEADER true)

DO $$ BEGIN
 IF (SELECT count(*) FROM stage_performance_profiles) <> 495 THEN
   RAISE EXCEPTION 'Performance profile row count != 495';
 END IF;
 IF EXISTS (SELECT 1 FROM stage_performance_profiles GROUP BY player_id HAVING count(*) <> 1) THEN
   RAISE EXCEPTION 'Duplicate staged player';
 END IF;
 IF EXISTS (SELECT 1 FROM stage_performance_profiles WHERE
      algorithm_version <> 'B49_FEATURES_V1' OR reference_season <> '2026/2027'
      OR role_code NOT IN ('P','D','C','A')
      OR history_seasons_available::int NOT BETWEEN 1 AND 2
      OR source_mix NOT IN ('TWO_FANTACALCIO','MIXED','TWO_INTERNATIONAL','ONE_SEASON_ONLY')
      OR small_sample_flag NOT IN ('YES','NO')
      OR data_confidence_score::numeric NOT BETWEEN 0 AND 100
      OR input_fingerprint !~ '^[0-9a-f]{64}$'
      OR jsonb_typeof(feature_values_json::jsonb) <> 'object'
      OR jsonb_typeof(evidence_json::jsonb) <> 'object'
      OR feature_values_json::jsonb->'historical_performance_score' <> 'null'::jsonb
      OR feature_values_json::jsonb->'cross_league_adjustment' <> 'null'::jsonb) THEN
   RAISE EXCEPTION 'Invalid staged performance contract';
 END IF;
 IF (SELECT count(*) FROM stage_performance_definitions) <> 19
    OR EXISTS (SELECT 1 FROM stage_performance_definitions GROUP BY feature_key HAVING count(*) <> 1)
    OR EXISTS (SELECT 1 FROM stage_performance_definitions
               WHERE algorithm_version <> 'B49_FEATURES_V1' OR null_policy <> 'NULL_STAYS_NULL') THEN
   RAISE EXCEPTION 'Invalid feature definitions';
 END IF;
 IF EXISTS (
   SELECT 1 FROM stage_performance_profiles s
   LEFT JOIN seasons se ON se.code=s.reference_season
   LEFT JOIN players p ON p.id=s.player_id::bigint
   LEFT JOIN clubs c ON c.id=s.current_club_id::bigint
   LEFT JOIN player_registrations r ON r.player_id=p.id AND r.season_id=se.id
   LEFT JOIN player_roles pr ON pr.player_id=p.id AND pr.season_id=se.id AND pr.source='FANTACALCIO'
   WHERE se.id IS NULL OR p.id IS NULL OR c.id IS NULL OR r.registration_status<>'ACTIVE'
      OR r.club_id<>c.id OR pr.fantasy_role<>s.role_code
 ) THEN RAISE EXCEPTION 'Current catalog identity/club/role gate failed'; END IF;
 IF (SELECT count(*) FROM player_registrations r JOIN seasons se ON se.id=r.season_id
     WHERE se.code='2026/2027' AND r.registration_status='ACTIVE') <> 495 THEN
   RAISE EXCEPTION 'Target operational population != 495';
 END IF;
 IF EXISTS (
   SELECT 1 FROM player_performance_algorithms a WHERE a.version='B49_FEATURES_V1'
     AND (a.reference_season_id<>(SELECT id FROM seasons WHERE code='2026/2027')
          OR a.model_decision<>'OVERALL_SCORE_DEFERRED_TO_BLOCK_50')
 ) THEN RAISE EXCEPTION 'Algorithm version conflict'; END IF;
 IF EXISTS (
   SELECT 1 FROM stage_performance_definitions s
   JOIN player_performance_feature_definitions d
     ON d.algorithm_version=s.algorithm_version AND d.feature_key=s.feature_key
   WHERE (d.semantic_class,d.unit,d.definition,d.null_policy)
      IS DISTINCT FROM (s.semantic_class,s.unit,s.definition,s.null_policy)
 ) THEN RAISE EXCEPTION 'Feature definition version conflict'; END IF;
 IF EXISTS (
   SELECT 1 FROM stage_performance_profiles s
   JOIN seasons se ON se.code=s.reference_season
   JOIN player_performance_feature_profiles p ON p.player_id=s.player_id::bigint
      AND p.reference_season_id=se.id AND p.algorithm_version=s.algorithm_version
   WHERE (p.current_club_id,p.role_code,p.history_seasons_available,p.latest_history_season,
          p.source_mix,p.small_sample,p.data_confidence_score,p.input_fingerprint,p.feature_values,p.evidence)
     IS DISTINCT FROM
         (s.current_club_id::bigint,s.role_code::char(1),s.history_seasons_available::smallint,
          s.latest_history_season,s.source_mix,(s.small_sample_flag='YES'),s.data_confidence_score::numeric,
          s.input_fingerprint::char(64),s.feature_values_json::jsonb,s.evidence_json::jsonb)
 ) THEN RAISE EXCEPTION 'Immutable performance profile version conflict'; END IF;
END $$;

\if :preflight
ROLLBACK;
\echo 'PLAYER PERFORMANCE PREFLIGHT SQL: PASS (NO PERSISTENT DML / NO NEXTVAL)'
\quit
\endif

INSERT INTO player_performance_algorithms(version,reference_season_id,status,model_decision)
SELECT 'B49_FEATURES_V1',id,'ACTIVE','OVERALL_SCORE_DEFERRED_TO_BLOCK_50'
FROM seasons WHERE code='2026/2027'
ON CONFLICT (version) DO NOTHING;

INSERT INTO player_performance_feature_definitions(
 algorithm_version,feature_key,semantic_class,unit,definition,null_policy)
SELECT algorithm_version,feature_key,semantic_class,unit,definition,null_policy
FROM stage_performance_definitions
ON CONFLICT (algorithm_version,feature_key) DO NOTHING;

INSERT INTO player_performance_feature_profiles(
 player_id,reference_season_id,current_club_id,role_code,algorithm_version,
 history_seasons_available,latest_history_season,source_mix,small_sample,
 data_confidence_score,input_fingerprint,feature_values,evidence)
SELECT s.player_id::bigint,se.id,s.current_club_id::bigint,s.role_code,s.algorithm_version,
 s.history_seasons_available::smallint,s.latest_history_season,s.source_mix,
 s.small_sample_flag='YES',s.data_confidence_score::numeric,s.input_fingerprint,
 s.feature_values_json::jsonb,s.evidence_json::jsonb
FROM stage_performance_profiles s JOIN seasons se ON se.code=s.reference_season
WHERE NOT EXISTS (
 SELECT 1 FROM player_performance_feature_profiles p
 WHERE p.player_id=s.player_id::bigint AND p.reference_season_id=se.id
   AND p.algorithm_version=s.algorithm_version
);

DO $$ BEGIN
 IF (SELECT count(*) FROM player_performance_feature_profiles
     WHERE algorithm_version='B49_FEATURES_V1') <> 495 THEN
   RAISE EXCEPTION 'Final performance profile count != 495';
 END IF;
 IF EXISTS (SELECT 1 FROM player_performance_feature_profiles
     WHERE algorithm_version='B49_FEATURES_V1'
       AND (data_confidence_score NOT BETWEEN 0 AND 100
            OR feature_values->'historical_performance_score'<>'null'::jsonb)) THEN
   RAISE EXCEPTION 'Final feature invariant failed';
 END IF;
END $$;

COMMIT;
\echo 'PLAYER PERFORMANCE IMPORT: PASS (COMMIT)'
