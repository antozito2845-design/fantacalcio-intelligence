INSERT INTO club_strength_ratings (
    club_id,
    season_id,
    overall_rating,
    attacking_rating,
    defensive_rating,
    home_rating,
    away_rating
)
SELECT
    club.id,
    season.id,

    CASE club.slug
        WHEN 'inter' THEN 91
        WHEN 'napoli' THEN 88
        WHEN 'juventus' THEN 86
        WHEN 'milan' THEN 85
        WHEN 'roma' THEN 82
        WHEN 'atalanta' THEN 81
        WHEN 'bologna' THEN 77
        WHEN 'lazio' THEN 76
        WHEN 'fiorentina' THEN 75
        ELSE 65
    END,

    CASE club.slug
        WHEN 'inter' THEN 92
        WHEN 'napoli' THEN 89
        WHEN 'milan' THEN 87
        WHEN 'juventus' THEN 84
        ELSE 66
    END,

    CASE club.slug
        WHEN 'inter' THEN 92
        WHEN 'juventus' THEN 88
        WHEN 'napoli' THEN 86
        WHEN 'roma' THEN 82
        ELSE 65
    END,

    CASE club.slug
        WHEN 'inter' THEN 94
        WHEN 'napoli' THEN 91
        WHEN 'juventus' THEN 89
        WHEN 'milan' THEN 88
        ELSE 68
    END,

    CASE club.slug
        WHEN 'inter' THEN 88
        WHEN 'napoli' THEN 85
        WHEN 'juventus' THEN 83
        WHEN 'milan' THEN 82
        ELSE 62
    END

FROM clubs club
JOIN seasons season
    ON season.code = '2026/2027'

ON CONFLICT (club_id, season_id)
DO UPDATE SET
    overall_rating = EXCLUDED.overall_rating,
    attacking_rating = EXCLUDED.attacking_rating,
    defensive_rating = EXCLUDED.defensive_rating,
    home_rating = EXCLUDED.home_rating,
    away_rating = EXCLUDED.away_rating,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO fixtures (
    season_id,
    matchday,
    home_club_id,
    away_club_id,
    kickoff_at
)
SELECT
    season.id,
    fixture_data.matchday,
    home_club.id,
    away_club.id,
    fixture_data.kickoff_at

FROM (
    VALUES
        (1, 'napoli', 'lecce', '2026-08-22 18:00:00+02'::timestamptz),
        (1, 'inter', 'roma', '2026-08-22 20:45:00+02'::timestamptz),
        (1, 'milan', 'venezia', '2026-08-23 18:00:00+02'::timestamptz),
        (1, 'juventus', 'atalanta', '2026-08-23 20:45:00+02'::timestamptz),

        (2, 'juventus', 'napoli', '2026-08-29 20:45:00+02'::timestamptz),
        (2, 'lecce', 'inter', '2026-08-30 18:00:00+02'::timestamptz),
        (2, 'roma', 'milan', '2026-08-30 20:45:00+02'::timestamptz),

        (3, 'napoli', 'venezia', '2026-09-12 18:00:00+02'::timestamptz),
        (3, 'inter', 'juventus', '2026-09-12 20:45:00+02'::timestamptz),
        (3, 'milan', 'lecce', '2026-09-13 18:00:00+02'::timestamptz)
) AS fixture_data(
    matchday,
    home_slug,
    away_slug,
    kickoff_at
)

JOIN seasons season
    ON season.code = '2026/2027'

JOIN clubs home_club
    ON home_club.slug = fixture_data.home_slug

JOIN clubs away_club
    ON away_club.slug = fixture_data.away_slug

ON CONFLICT (
    season_id,
    matchday,
    home_club_id,
    away_club_id
)
DO NOTHING;