INSERT INTO player_availabilities (
    player_id,
    season_id,
    matchday,
    status,
    starting_probability,
    availability_probability,
    injury_description,
    source
)
SELECT
    player.id,
    season.id,
    1,
    'PROBABLE',
    88,
    96,
    NULL,
    'MANUAL'
FROM players player
JOIN seasons season
    ON season.code = '2026/2027'
WHERE player.normalized_name = 'giocatore test uno'

ON CONFLICT (player_id, season_id, matchday)
DO UPDATE SET
    status = EXCLUDED.status,
    starting_probability =
        EXCLUDED.starting_probability,
    availability_probability =
        EXCLUDED.availability_probability,
    injury_description =
        EXCLUDED.injury_description,
    source = EXCLUDED.source,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO player_availabilities (
    player_id,
    season_id,
    matchday,
    status,
    starting_probability,
    availability_probability,
    injury_description,
    expected_return_date,
    source
)
SELECT
    player.id,
    season.id,
    1,
    'DOUBTFUL',
    35,
    55,
    'Affaticamento muscolare',
    '2026-08-25',
    'MANUAL'
FROM players player
JOIN seasons season
    ON season.code = '2026/2027'
WHERE player.normalized_name = 'giocatore test due'

ON CONFLICT (player_id, season_id, matchday)
DO UPDATE SET
    status = EXCLUDED.status,
    starting_probability =
        EXCLUDED.starting_probability,
    availability_probability =
        EXCLUDED.availability_probability,
    injury_description =
        EXCLUDED.injury_description,
    expected_return_date =
        EXCLUDED.expected_return_date,
    source = EXCLUDED.source,
    updated_at = CURRENT_TIMESTAMP;