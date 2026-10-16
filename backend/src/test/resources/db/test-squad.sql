-- Test-only replacement for the historical V17 squad removed by V34/V59.
-- Production Flyway locations do not include this resource.
SELECT setval('players_id_seq', 1, FALSE)
WHERE NOT EXISTS (SELECT 1 FROM players);

INSERT INTO fantasy_squads (
    league_configuration_id,
    name,
    initial_budget,
    active,
    owner_username
)
SELECT
    league.id,
    'La mia rosa',
    league.total_budget,
    TRUE,
    'legacy-owner'
FROM league_configurations league
WHERE league.active = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM fantasy_squads squad
      WHERE squad.owner_username = 'legacy-owner'
        AND squad.name = 'La mia rosa'
  );
