UPDATE player_roles
SET fantasy_role = 'A'
WHERE player_id = 1
  AND source = 'FANTACALCIO'
  AND season_id = (
      SELECT id
      FROM seasons
      WHERE code = '2026/2027'
  );