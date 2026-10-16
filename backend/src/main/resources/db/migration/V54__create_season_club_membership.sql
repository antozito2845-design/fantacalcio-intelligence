CREATE TABLE season_clubs (
    season_id BIGINT NOT NULL REFERENCES seasons(id),
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_season_clubs PRIMARY KEY (season_id, club_id)
);

CREATE INDEX idx_season_clubs_club_season
    ON season_clubs(club_id, season_id);

-- The current Serie A catalog was explicitly seeded for 2026/2027 in V4.
INSERT INTO season_clubs (season_id, club_id)
SELECT season.id, club.id
FROM seasons season
JOIN clubs club ON club.slug IN (
    'atalanta', 'bologna', 'cagliari', 'como', 'fiorentina',
    'frosinone', 'genoa', 'inter', 'juventus', 'lazio',
    'lecce', 'milan', 'monza', 'napoli', 'parma', 'roma',
    'sassuolo', 'torino', 'udinese', 'venezia'
)
WHERE season.code = '2026/2027'
ON CONFLICT (season_id, club_id) DO NOTHING;

-- Existing season-scoped registrations and fixtures are authoritative evidence
-- of historical membership; no current club set is projected onto other seasons.
INSERT INTO season_clubs (season_id, club_id)
SELECT DISTINCT registration.season_id, registration.club_id
FROM player_registrations registration
ON CONFLICT (season_id, club_id) DO NOTHING;

INSERT INTO season_clubs (season_id, club_id)
SELECT fixture.season_id, membership.club_id
FROM fixtures fixture
CROSS JOIN LATERAL (
    VALUES (fixture.home_club_id), (fixture.away_club_id)
) AS membership(club_id)
ON CONFLICT (season_id, club_id) DO NOTHING;
