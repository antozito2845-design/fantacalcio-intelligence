-- Remove only residues whose exact values were introduced by V8, V13, V16 and V17.
DELETE FROM player_availabilities availability
USING players player, seasons season
WHERE availability.player_id = player.id
  AND current_database() <> 'fantacalcio_intelligence_test'
  AND availability.season_id = season.id
  AND season.code = '2026/2027'
  AND availability.matchday = 1
  AND player.normalized_name IN ('giocatore test uno', 'giocatore test due');

DELETE FROM fixtures fixture
USING seasons season
WHERE fixture.season_id = season.id
  AND current_database() <> 'fantacalcio_intelligence_test'
  AND season.code = '2026/2027'
  AND fixture.kickoff_at IN (
      '2026-08-22 18:00:00+02'::timestamptz,
      '2026-08-22 20:45:00+02'::timestamptz,
      '2026-08-23 18:00:00+02'::timestamptz,
      '2026-08-23 20:45:00+02'::timestamptz,
      '2026-08-29 20:45:00+02'::timestamptz,
      '2026-08-30 18:00:00+02'::timestamptz,
      '2026-08-30 20:45:00+02'::timestamptz,
      '2026-09-12 18:00:00+02'::timestamptz,
      '2026-09-12 20:45:00+02'::timestamptz,
      '2026-09-13 18:00:00+02'::timestamptz
  );

DELETE FROM club_season_tactics tactic
USING coaches coach, seasons season
WHERE tactic.coach_id = coach.id
  AND current_database() <> 'fantacalcio_intelligence_test'
  AND tactic.season_id = season.id
  AND season.code = '2026/2027'
  AND coach.normalized_name IN ('allenatore napoli test', 'allenatore inter test');

DELETE FROM coaches
WHERE normalized_name IN ('allenatore napoli test', 'allenatore inter test')
  AND current_database() <> 'fantacalcio_intelligence_test'
  AND NOT EXISTS (
      SELECT 1 FROM club_season_tactics tactic WHERE tactic.coach_id = coaches.id
  );

-- V16 generated a complete synthetic rating set before real refreshes existed.
DELETE FROM club_strength_ratings rating
USING seasons season, clubs club
WHERE rating.season_id = season.id
  AND current_database() <> 'fantacalcio_intelligence_test'
  AND rating.club_id = club.id
  AND season.code = '2026/2027'
  AND rating.overall_rating = CASE club.slug
      WHEN 'inter' THEN 91 WHEN 'napoli' THEN 88 WHEN 'juventus' THEN 86
      WHEN 'milan' THEN 85 WHEN 'roma' THEN 82 WHEN 'atalanta' THEN 81
      WHEN 'bologna' THEN 77 WHEN 'lazio' THEN 76 WHEN 'fiorentina' THEN 75 ELSE 65 END
  AND rating.attacking_rating = CASE club.slug
      WHEN 'inter' THEN 92 WHEN 'napoli' THEN 89 WHEN 'milan' THEN 87
      WHEN 'juventus' THEN 84 ELSE 66 END
  AND rating.defensive_rating = CASE club.slug
      WHEN 'inter' THEN 92 WHEN 'juventus' THEN 88 WHEN 'napoli' THEN 86
      WHEN 'roma' THEN 82 ELSE 65 END
  AND rating.home_rating = CASE club.slug
      WHEN 'inter' THEN 94 WHEN 'napoli' THEN 91 WHEN 'juventus' THEN 89
      WHEN 'milan' THEN 88 ELSE 68 END
  AND rating.away_rating = CASE club.slug
      WHEN 'inter' THEN 88 WHEN 'napoli' THEN 85 WHEN 'juventus' THEN 83
      WHEN 'milan' THEN 82 ELSE 62 END;

DELETE FROM fantasy_squads squad
USING league_configurations league
WHERE current_database() <> 'fantacalcio_intelligence_test'
  AND squad.league_configuration_id = league.id
  AND squad.owner_username = 'legacy-owner'
  AND squad.name = 'La mia rosa'
  AND squad.initial_budget = league.total_budget
  AND squad.active = TRUE
  AND squad.created_at = squad.updated_at
  AND NOT EXISTS (
      SELECT 1 FROM fantasy_squad_players player WHERE player.squad_id = squad.id
  );
