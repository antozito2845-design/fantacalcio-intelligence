\pset pager off

\echo '============================================================'
\echo 'B67 FINAL PREFLIGHT'
\echo '============================================================'

\echo ''
\echo '=== 1. OPERATIONAL CATALOGUE ==='

WITH operational AS (
    SELECT
        p.id,
        p.display_name,
        r.club_id,
        pr.fantasy_role
    FROM players p
    JOIN player_registrations r
      ON r.player_id=p.id
    JOIN seasons s
      ON s.id=r.season_id
    JOIN player_roles pr
      ON pr.player_id=p.id
     AND pr.season_id=s.id
     AND pr.source='FANTACALCIO'
    WHERE s.code='2026/2027'
      AND r.registration_status='ACTIVE'
      AND p.active=TRUE
)
SELECT
    COUNT(*) AS rows,
    COUNT(DISTINCT id) AS distinct_players
FROM operational;

\echo ''
\echo '=== 2. DUPLICATE OPERATIONAL REGISTRATIONS ==='

SELECT
    r.player_id,
    COUNT(*) AS active_registrations
FROM player_registrations r
JOIN seasons s
  ON s.id=r.season_id
WHERE s.code='2026/2027'
  AND r.registration_status='ACTIVE'
GROUP BY r.player_id
HAVING COUNT(*) > 1
ORDER BY r.player_id;

\echo ''
\echo '=== 3. DUPLICATE FANTACALCIO ROLES ==='

SELECT
    pr.player_id,
    COUNT(*) AS role_rows
FROM player_roles pr
JOIN seasons s
  ON s.id=pr.season_id
WHERE s.code='2026/2027'
  AND pr.source='FANTACALCIO'
GROUP BY pr.player_id
HAVING COUNT(*) > 1
ORDER BY pr.player_id;

\echo ''
\echo '=== 4. DUPLICATE FANTACALCIO MARKET ROWS ==='

SELECT
    m.player_id,
    COUNT(*) AS market_rows
FROM player_market_values m
JOIN seasons s
  ON s.id=m.season_id
WHERE s.code='2026/2027'
  AND m.source='FANTACALCIO'
GROUP BY m.player_id
HAVING COUNT(*) > 1
ORDER BY m.player_id;

\echo ''
\echo '=== 5. B66 V3 COVERAGE + ROLE CONSISTENCY ==='

WITH operational AS (
    SELECT DISTINCT
        p.id,
        p.display_name,
        pr.fantasy_role
    FROM players p
    JOIN player_registrations r
      ON r.player_id=p.id
    JOIN seasons s
      ON s.id=r.season_id
    JOIN player_roles pr
      ON pr.player_id=p.id
     AND pr.season_id=s.id
     AND pr.source='FANTACALCIO'
    WHERE s.code='2026/2027'
      AND r.registration_status='ACTIVE'
      AND p.active=TRUE
)
SELECT
    COUNT(*) AS operational,
    COUNT(pf.player_id) AS b66_v3,
    COUNT(*) FILTER (
        WHERE pf.player_id IS NULL
    ) AS missing_b66,
    COUNT(*) FILTER (
        WHERE pf.player_id IS NOT NULL
          AND pf.role_code <> o.fantasy_role
    ) AS role_mismatch
FROM operational o
LEFT JOIN player_performance_feature_profiles pf
  ON pf.player_id=o.id
 AND pf.algorithm_version='B66_FEATURES_V3';

\echo ''
\echo '=== 6. B66 ROLE MISMATCH DETAILS ==='

WITH operational AS (
    SELECT DISTINCT
        p.id,
        p.display_name,
        pr.fantasy_role
    FROM players p
    JOIN player_registrations r
      ON r.player_id=p.id
    JOIN seasons s
      ON s.id=r.season_id
    JOIN player_roles pr
      ON pr.player_id=p.id
     AND pr.season_id=s.id
     AND pr.source='FANTACALCIO'
    WHERE s.code='2026/2027'
      AND r.registration_status='ACTIVE'
      AND p.active=TRUE
)
SELECT
    o.id,
    o.display_name,
    o.fantasy_role AS fantacalcio_role,
    pf.role_code AS b66_role
FROM operational o
JOIN player_performance_feature_profiles pf
  ON pf.player_id=o.id
 AND pf.algorithm_version='B66_FEATURES_V3'
WHERE pf.role_code <> o.fantasy_role
ORDER BY o.id;

\echo ''
\echo '=== 7. OPERATIONAL MARKET COVERAGE ==='

WITH operational AS (
    SELECT DISTINCT p.id
    FROM players p
    JOIN player_registrations r
      ON r.player_id=p.id
    JOIN seasons s
      ON s.id=r.season_id
    WHERE s.code='2026/2027'
      AND r.registration_status='ACTIVE'
      AND p.active=TRUE
)
SELECT
    COUNT(*) AS operational,
    COUNT(m.player_id) AS market_rows,
    COUNT(*) FILTER (
        WHERE m.player_id IS NULL
    ) AS missing_market,
    COUNT(*) FILTER (
        WHERE m.quotation IS NULL
    ) AS missing_quotation,
    COUNT(*) FILTER (
        WHERE m.fantasy_market_value IS NULL
    ) AS missing_fvm
FROM operational o
LEFT JOIN seasons s
  ON s.code='2026/2027'
LEFT JOIN player_market_values m
  ON m.player_id=o.id
 AND m.season_id=s.id
 AND m.source='FANTACALCIO';

\echo ''
\echo '=== 8. OPERATIONAL FVM DISTRIBUTION ==='

WITH operational AS (
    SELECT DISTINCT
        p.id,
        pr.fantasy_role
    FROM players p
    JOIN player_registrations r
      ON r.player_id=p.id
    JOIN seasons s
      ON s.id=r.season_id
    JOIN player_roles pr
      ON pr.player_id=p.id
     AND pr.season_id=s.id
     AND pr.source='FANTACALCIO'
    WHERE s.code='2026/2027'
      AND r.registration_status='ACTIVE'
      AND p.active=TRUE
)
SELECT
    o.fantasy_role,
    COUNT(*) AS players,
    MIN(m.fantasy_market_value) AS min_fvm,
    percentile_cont(0.25) WITHIN GROUP (
        ORDER BY m.fantasy_market_value
    ) AS p25_fvm,
    percentile_cont(0.50) WITHIN GROUP (
        ORDER BY m.fantasy_market_value
    ) AS median_fvm,
    percentile_cont(0.75) WITHIN GROUP (
        ORDER BY m.fantasy_market_value
    ) AS p75_fvm,
    MAX(m.fantasy_market_value) AS max_fvm
FROM operational o
JOIN seasons s
  ON s.code='2026/2027'
JOIN player_market_values m
  ON m.player_id=o.id
 AND m.season_id=s.id
 AND m.source='FANTACALCIO'
GROUP BY o.fantasy_role
ORDER BY o.fantasy_role;

\echo ''
\echo '=== 9. B50 CURRENT COVERAGE ==='

SELECT
    COUNT(*) AS b50_profiles,
    COUNT(DISTINCT player_id) AS distinct_players
FROM player_value_profiles
WHERE algorithm_version='B50_FANTASY_VALUE_V1';

\echo ''
\echo '=== 10. OPERATIONAL PLAYERS ABSENT FROM B50 ==='

WITH operational AS (
    SELECT DISTINCT
        p.id,
        p.display_name,
        c.name AS club,
        pr.fantasy_role
    FROM players p
    JOIN player_registrations r
      ON r.player_id=p.id
    JOIN seasons s
      ON s.id=r.season_id
    JOIN clubs c
      ON c.id=r.club_id
    JOIN player_roles pr
      ON pr.player_id=p.id
     AND pr.season_id=s.id
     AND pr.source='FANTACALCIO'
    WHERE s.code='2026/2027'
      AND r.registration_status='ACTIVE'
      AND p.active=TRUE
)
SELECT
    o.id,
    o.display_name,
    o.club,
    o.fantasy_role,
    pf.source_mix,
    pf.history_seasons_available,
    pf.data_confidence_score,
    m.quotation,
    m.fantasy_market_value
FROM operational o
LEFT JOIN player_value_profiles v
  ON v.player_id=o.id
 AND v.algorithm_version='B50_FANTASY_VALUE_V1'
JOIN player_performance_feature_profiles pf
  ON pf.player_id=o.id
 AND pf.algorithm_version='B66_FEATURES_V3'
JOIN seasons s
  ON s.code='2026/2027'
LEFT JOIN player_market_values m
  ON m.player_id=o.id
 AND m.season_id=s.id
 AND m.source='FANTACALCIO'
WHERE v.player_id IS NULL
ORDER BY o.id;

\echo ''
\echo '=== 11. B51 CURRENT COVERAGE ==='

SELECT
    pricing_status,
    COUNT(*) AS profiles
FROM auction_price_profiles
WHERE algorithm_version='B51_AUCTION_PRICE_V1'
  AND league_teams=8
  AND total_budget=1000
  AND defense_modifier=FALSE
GROUP BY pricing_status
ORDER BY pricing_status;

\echo ''
\echo '=== 12. OPERATIONAL PLAYERS ABSENT FROM B51 ==='

WITH operational AS (
    SELECT DISTINCT
        p.id,
        p.display_name,
        c.name AS club,
        pr.fantasy_role
    FROM players p
    JOIN player_registrations r
      ON r.player_id=p.id
    JOIN seasons s
      ON s.id=r.season_id
    JOIN clubs c
      ON c.id=r.club_id
    JOIN player_roles pr
      ON pr.player_id=p.id
     AND pr.season_id=s.id
     AND pr.source='FANTACALCIO'
    WHERE s.code='2026/2027'
      AND r.registration_status='ACTIVE'
      AND p.active=TRUE
)
SELECT
    o.id,
    o.display_name,
    o.club,
    o.fantasy_role,
    pf.source_mix,
    pf.history_seasons_available,
    pf.data_confidence_score
FROM operational o
LEFT JOIN auction_price_profiles ap
  ON ap.player_id=o.id
 AND ap.algorithm_version='B51_AUCTION_PRICE_V1'
 AND ap.league_teams=8
 AND ap.total_budget=1000
 AND ap.defense_modifier=FALSE
JOIN player_performance_feature_profiles pf
  ON pf.player_id=o.id
 AND pf.algorithm_version='B66_FEATURES_V3'
WHERE ap.player_id IS NULL
ORDER BY o.id;

\echo ''
\echo '=== 13. B66 DEFINED FEATURE KEYS ==='

SELECT feature_key
FROM player_performance_feature_definitions
WHERE algorithm_version='B66_FEATURES_V3'
ORDER BY feature_key;

\echo ''
\echo '=== 14. ACTUAL JSON FEATURE KEYS ==='

SELECT DISTINCT key
FROM player_performance_feature_profiles pf
CROSS JOIN LATERAL jsonb_object_keys(pf.feature_values) AS key
WHERE pf.algorithm_version='B66_FEATURES_V3'
ORDER BY key;

\echo ''
\echo '=== 15. JSON KEYS WITHOUT DEFINITION ==='

WITH actual AS (
    SELECT DISTINCT key
    FROM player_performance_feature_profiles pf
    CROSS JOIN LATERAL
        jsonb_object_keys(pf.feature_values) AS key
    WHERE pf.algorithm_version='B66_FEATURES_V3'
),
defined AS (
    SELECT feature_key AS key
    FROM player_performance_feature_definitions
    WHERE algorithm_version='B66_FEATURES_V3'
)
SELECT actual.key
FROM actual
LEFT JOIN defined USING(key)
WHERE defined.key IS NULL
ORDER BY actual.key;

\echo ''
\echo '=== 16. DEFINITIONS WITHOUT JSON KEY ==='

WITH actual AS (
    SELECT DISTINCT key
    FROM player_performance_feature_profiles pf
    CROSS JOIN LATERAL
        jsonb_object_keys(pf.feature_values) AS key
    WHERE pf.algorithm_version='B66_FEATURES_V3'
),
defined AS (
    SELECT feature_key AS key
    FROM player_performance_feature_definitions
    WHERE algorithm_version='B66_FEATURES_V3'
)
SELECT defined.key
FROM defined
LEFT JOIN actual USING(key)
WHERE actual.key IS NULL
ORDER BY defined.key;

\echo ''
\echo '=== 17. PENE V NO-HISTORY NON-NULL FEATURE VALUES ==='

SELECT
    pf.player_id,
    p.display_name,
    kv.key,
    kv.value
FROM player_performance_feature_profiles pf
JOIN players p
  ON p.id=pf.player_id
CROSS JOIN LATERAL
    jsonb_each(pf.feature_values) kv
WHERE pf.algorithm_version='B66_FEATURES_V3'
  AND pf.source_mix='NO_HISTORY'
  AND kv.value <> 'null'::jsonb
ORDER BY kv.key;

\echo ''
\echo '=== 18. AUCTION ALGORITHMS ==='

SELECT *
FROM auction_price_algorithms
ORDER BY created_at,version;

\echo ''
\echo '=== 19. PERFORMANCE ALGORITHMS ==='

SELECT
    version,
    status,
    model_decision
FROM player_performance_algorithms
ORDER BY version;

\echo ''
\echo '=== 20. B66 SOURCE MIX / CONFIDENCE ==='

SELECT
    role_code,
    source_mix,
    COUNT(*) AS profiles,
    MIN(data_confidence_score) AS min_confidence,
    ROUND(AVG(data_confidence_score),2) AS avg_confidence,
    MAX(data_confidence_score) AS max_confidence
FROM player_performance_feature_profiles
WHERE algorithm_version='B66_FEATURES_V3'
GROUP BY role_code,source_mix
ORDER BY role_code,source_mix;