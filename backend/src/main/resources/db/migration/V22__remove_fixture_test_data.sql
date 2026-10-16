WITH test_fixtures (
    matchday,
    home_slug,
    away_slug
) AS (
    VALUES
        (1, 'napoli', 'lecce'),
        (1, 'inter', 'roma'),
        (1, 'milan', 'venezia'),
        (1, 'juventus', 'atalanta'),

        (2, 'juventus', 'napoli'),
        (2, 'lecce', 'inter'),
        (2, 'roma', 'milan'),

        (3, 'napoli', 'venezia'),
        (3, 'inter', 'juventus'),
        (3, 'milan', 'lecce'),

        (12, 'inter', 'napoli'),
        (12, 'milan', 'roma')
)
DELETE FROM fixtures fixture
USING
    seasons season,
    clubs home_club,
    clubs away_club,
    test_fixtures test_fixture
WHERE fixture.season_id = season.id
  AND fixture.home_club_id = home_club.id
  AND fixture.away_club_id = away_club.id
  AND season.code = '2026/2027'
  AND fixture.matchday = test_fixture.matchday
  AND home_club.slug = test_fixture.home_slug
  AND away_club.slug = test_fixture.away_slug;