\pset pager off

\echo '=== B66 FEATURE DEFINITIONS ==='

SELECT
    feature_key,
    semantic_class,
    unit,
    definition,
    null_policy
FROM player_performance_feature_definitions
WHERE algorithm_version='B66_FEATURES_V3'
ORDER BY feature_key;

\echo '=== OPERATIONAL COVERAGE ==='

WITH operational AS (
    SELECT DISTINCT
        p.id,
        p.display_name,
        pr.fantasy_role,
        c.name AS club
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
    COUNT(*) AS operational_players,
    COUNT(pf.player_id) AS b66_profiles,
    COUNT(m.player_id) AS market_rows,
    COUNT(*) FILTER (
        WHERE m.fantasy_market_value IS NOT NULL
    ) AS with_fvm,
    COUNT(*) FILTER (
        WHERE m.quotation IS NOT NULL
    ) AS with_quotation
FROM operational o
LEFT JOIN player_performance_feature_profiles pf
  ON pf.player_id=o.id
 AND pf.algorithm_version='B66_FEATURES_V3'
LEFT JOIN seasons s
  ON s.code='2026/2027'
LEFT JOIN player_market_values m
  ON m.player_id=o.id
 AND m.season_id=s.id
 AND m.source='FANTACALCIO';

\echo '=== COVERAGE BY ROLE ==='

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
    COUNT(*) FILTER (
        WHERE pf.source_mix='NO_HISTORY'
    ) AS no_history,
    COUNT(*) FILTER (
        WHERE m.fantasy_market_value IS NOT NULL
    ) AS with_fvm
FROM operational o
LEFT JOIN player_performance_feature_profiles pf
  ON pf.player_id=o.id
 AND pf.algorithm_version='B66_FEATURES_V3'
LEFT JOIN seasons s
  ON s.code='2026/2027'
LEFT JOIN player_market_values m
  ON m.player_id=o.id
 AND m.season_id=s.id
 AND m.source='FANTACALCIO'
GROUP BY o.fantasy_role
ORDER BY o.fantasy_role;

\echo '=== FVM DISTRIBUTION BY ROLE ==='

SELECT
    pr.fantasy_role,
    COUNT(*) FILTER (
        WHERE m.fantasy_market_value IS NOT NULL
    ) AS samples,
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
FROM player_market_values m
JOIN seasons s
  ON s.id=m.season_id
JOIN player_roles pr
  ON pr.player_id=m.player_id
 AND pr.season_id=m.season_id
 AND pr.source='FANTACALCIO'
WHERE s.code='2026/2027'
  AND m.source='FANTACALCIO'
GROUP BY pr.fantasy_role
ORDER BY pr.fantasy_role;

\echo '=== B66 SOURCE MIX ==='

SELECT
    role_code,
    source_mix,
    COUNT(*) AS players,
    ROUND(AVG(data_confidence_score),2) AS avg_confidence
FROM player_performance_feature_profiles
WHERE algorithm_version='B66_FEATURES_V3'
GROUP BY role_code,source_mix
ORDER BY role_code,source_mix;

\echo '=== SAMPLE FEATURE VALUES ==='

SELECT
    pf.player_id,
    p.display_name,
    pf.role_code,
    pf.source_mix,
    pf.history_seasons_available,
    pf.data_confidence_score,
    pf.feature_values
FROM player_performance_feature_profiles pf
JOIN players p
  ON p.id=pf.player_id
WHERE pf.algorithm_version='B66_FEATURES_V3'
ORDER BY
    pf.role_code,
    pf.data_confidence_score DESC,
    pf.player_id
LIMIT 20;

\echo '=== NO HISTORY PLAYER ==='

SELECT
    pf.player_id,
    p.display_name,
    pf.role_code,
    pf.source_mix,
    pf.data_confidence_score,
    pf.feature_values,
    m.quotation,
    m.fantasy_market_value
FROM player_performance_feature_profiles pf
JOIN players p
  ON p.id=pf.player_id
JOIN seasons s
  ON s.id=pf.reference_season_id
LEFT JOIN player_market_values m
  ON m.player_id=pf.player_id
 AND m.season_id=s.id
 AND m.source='FANTACALCIO'
WHERE pf.algorithm_version='B66_FEATURES_V3'
  AND pf.source_mix='NO_HISTORY';

\echo '=== CURRENT B51 COVERAGE ==='

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