-- B66.1 - Correct verified Transfermarkt competition classifications.
-- These corrections are source semantics, not invented player statistics.

UPDATE player_competition_season_statistics
SET competition_type = 'DOMESTIC_CUP'
WHERE source = 'TRANSFERMARKT'
  AND competition_code IN ('FRC', 'POPO')
  AND competition_type <> 'DOMESTIC_CUP';

UPDATE player_competition_season_statistics
SET competition_type = 'INTERNATIONAL_CUP'
WHERE source = 'TRANSFERMARKT'
  AND competition_code = 'CS'
  AND competition_type <> 'INTERNATIONAL_CUP';