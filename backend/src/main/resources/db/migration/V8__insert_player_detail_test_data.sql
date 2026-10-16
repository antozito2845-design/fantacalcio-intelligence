INSERT INTO coaches (
    full_name,
    normalized_name,
    nationality_code
)
VALUES
    ('Allenatore Napoli Test', 'allenatore napoli test', 'IT'),
    ('Allenatore Inter Test', 'allenatore inter test', 'IT')
ON CONFLICT (normalized_name) DO NOTHING;

INSERT INTO club_season_tactics (
    club_id,
    season_id,
    coach_id,
    primary_formation,
    secondary_formation,
    playing_style,
    defensive_line,
    pressing_level
)
SELECT
    c.id,
    s.id,
    coach.id,
    '4-3-3',
    '4-2-3-1',
    'Possesso e sviluppo offensivo',
    'MEDIA',
    'ALTO'
FROM clubs c
JOIN seasons s ON s.code = '2026/2027'
JOIN coaches coach
    ON coach.normalized_name = 'allenatore napoli test'
WHERE c.slug = 'napoli'
ON CONFLICT (club_id, season_id) DO NOTHING;

INSERT INTO club_season_tactics (
    club_id,
    season_id,
    coach_id,
    primary_formation,
    secondary_formation,
    playing_style,
    defensive_line,
    pressing_level
)
SELECT
    c.id,
    s.id,
    coach.id,
    '3-5-2',
    '3-4-2-1',
    'Costruzione dal basso e occupazione ampiezza',
    'ALTA',
    'MEDIO'
FROM clubs c
JOIN seasons s ON s.code = '2026/2027'
JOIN coaches coach
    ON coach.normalized_name = 'allenatore inter test'
WHERE c.slug = 'inter'
ON CONFLICT (club_id, season_id) DO NOTHING;

INSERT INTO player_tactical_profiles (
    player_id,
    season_id,
    real_position,
    secondary_position,
    tactical_role,
    attacking_involvement,
    defensive_involvement,
    penalty_taker,
    projected_starter,
    starter_probability,
    competition_level,
    tactical_notes
)
SELECT
    p.id,
    s.id,
    'Punta centrale',
    'Seconda punta',
    'Finalizzatore',
    88,
    22,
    TRUE,
    TRUE,
    82,
    'MEDIA',
    'Profilo offensivo con buona presenza in area.'
FROM players p
JOIN seasons s ON s.code = '2026/2027'
WHERE p.normalized_name = 'giocatore test uno'
ON CONFLICT (player_id, season_id) DO NOTHING;

INSERT INTO player_tactical_profiles (
    player_id,
    season_id,
    real_position,
    secondary_position,
    tactical_role,
    attacking_involvement,
    defensive_involvement,
    projected_starter,
    starter_probability,
    competition_level,
    tactical_notes
)
SELECT
    p.id,
    s.id,
    'Mezzala offensiva',
    'Trequartista',
    'Centrocampista di inserimento',
    76,
    55,
    TRUE,
    74,
    'ALTA',
    'Può giocare più avanti rispetto al ruolo di listone.'
FROM players p
JOIN seasons s ON s.code = '2026/2027'
WHERE p.normalized_name = 'giocatore test due'
ON CONFLICT (player_id, season_id) DO NOTHING;

INSERT INTO player_evaluations (
    player_id,
    season_id,
    projected_fantasy_average,
    projected_appearances,
    projected_starts,
    projected_goals,
    projected_assists,
    reliability_score,
    potential_score,
    risk_score,
    value_score,
    evaluation_summary
)
SELECT
    p.id,
    s.id,
    7.10,
    32,
    27,
    14,
    5,
    78,
    86,
    34,
    84,
    'Attaccante con buon potenziale realizzativo e titolarità elevata.'
FROM players p
JOIN seasons s ON s.code = '2026/2027'
WHERE p.normalized_name = 'giocatore test uno'
ON CONFLICT (player_id, season_id) DO NOTHING;

INSERT INTO player_evaluations (
    player_id,
    season_id,
    projected_fantasy_average,
    projected_appearances,
    projected_starts,
    projected_goals,
    projected_assists,
    reliability_score,
    potential_score,
    risk_score,
    value_score,
    evaluation_summary
)
SELECT
    p.id,
    s.id,
    6.65,
    31,
    23,
    6,
    7,
    71,
    79,
    42,
    81,
    'Centrocampista sottovalutabile grazie alla posizione offensiva reale.'
FROM players p
JOIN seasons s ON s.code = '2026/2027'
WHERE p.normalized_name = 'giocatore test due'
ON CONFLICT (player_id, season_id) DO NOTHING;

INSERT INTO player_season_statistics (
    player_id,
    club_id,
    season_id,
    source,
    appearances,
    starts,
    minutes_played,
    average_rating,
    fantasy_average,
    goals,
    assists,
    yellow_cards,
    red_cards,
    expected_goals,
    expected_assists
)
SELECT
    p.id,
    c.id,
    s.id,
    'INTERNAL',
    31,
    25,
    2240,
    6.35,
    7.05,
    13,
    4,
    3,
    0,
    12.40,
    4.10
FROM players p
JOIN clubs c ON c.slug = 'napoli'
JOIN seasons s ON s.code = '2025/2026'
WHERE p.normalized_name = 'giocatore test uno'
ON CONFLICT (player_id, season_id, source) DO NOTHING;

INSERT INTO player_season_statistics (
    player_id,
    club_id,
    season_id,
    source,
    appearances,
    starts,
    minutes_played,
    average_rating,
    fantasy_average,
    goals,
    assists,
    yellow_cards,
    red_cards,
    expected_goals,
    expected_assists
)
SELECT
    p.id,
    c.id,
    s.id,
    'INTERNAL',
    29,
    21,
    1900,
    6.20,
    6.72,
    9,
    5,
    4,
    0,
    8.80,
    4.70
FROM players p
JOIN clubs c ON c.slug = 'napoli'
JOIN seasons s ON s.code = '2024/2025'
WHERE p.normalized_name = 'giocatore test uno'
ON CONFLICT (player_id, season_id, source) DO NOTHING;