\pset pager off

SELECT
    version,
    status,
    model_decision
FROM player_performance_algorithms
WHERE version IN (
    'B49_FEATURES_V1',
    'B66_FEATURES_V1',
    'B66_FEATURES_V2'
)
ORDER BY version;

SELECT
    algorithm_version,
    count(*) AS profiles,
    count(*) FILTER (
        WHERE source_mix = 'NO_HISTORY'
    ) AS no_history,
    min(history_seasons_available) AS min_history,
    max(history_seasons_available) AS max_history
FROM player_performance_feature_profiles
WHERE algorithm_version IN (
    'B66_FEATURES_V1',
    'B66_FEATURES_V2'
)
GROUP BY algorithm_version
ORDER BY algorithm_version;

SELECT
    p.id,
    p.display_name,
    pf.source_mix,
    pf.history_seasons_available,
    pf.latest_history_season,
    pf.data_confidence_score
FROM player_performance_feature_profiles pf
JOIN players p
  ON p.id = pf.player_id
WHERE pf.algorithm_version = 'B66_FEATURES_V2'
  AND pf.source_mix = 'NO_HISTORY';

SELECT
    count(*) AS transfermarkt_statistics
FROM player_competition_season_statistics
WHERE source = 'TRANSFERMARKT';

SELECT
    count(DISTINCT player_id)
        FILTER (
            WHERE player_id IN (
                1032,1033,1034,1035,
                1036,1037,1038,1111
            )
        ) AS recovered_players
FROM player_competition_season_statistics
WHERE source = 'TRANSFERMARKT';

SELECT
    competition_code,
    competition_type,
    count(*) AS rows
FROM player_competition_season_statistics
WHERE source = 'TRANSFERMARKT'
  AND competition_code IN (
      'CS','FRC','POPO'
  )
GROUP BY
    competition_code,
    competition_type
ORDER BY competition_code;