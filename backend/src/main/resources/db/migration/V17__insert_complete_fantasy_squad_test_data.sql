-- ============================================================
-- V17 - ROSA COMPLETA PREMIUM DI TEST
-- Stagione 2026/2027
-- Composizione finale: 3P - 8D - 8C - 6A
-- ============================================================

-- ------------------------------------------------------------
-- 1. CREAZIONE DEI 23 NUOVI GIOCATORI
-- I due giocatori già esistenti sono:
-- - giocatore test uno
-- - giocatore test due
-- ------------------------------------------------------------

INSERT INTO players (
    first_name,
    last_name,
    display_name,
    normalized_name,
    birth_date,
    nationality_code,
    preferred_foot,
    height_cm,
    active
)
SELECT
    data.first_name,
    data.last_name,
    data.display_name,
    data.normalized_name,
    data.birth_date,
    data.nationality_code,
    data.preferred_foot,
    data.height_cm,
    TRUE
FROM (
    VALUES
        -- Portieri
        ('Andrea', 'Valenti', 'Andrea Valenti',
         'andrea valenti', '1997-03-14'::date,
         'IT', 'RIGHT', 191),

        ('Marco', 'Rinaldi', 'Marco Rinaldi',
         'marco rinaldi', '1994-11-02'::date,
         'IT', 'RIGHT', 188),

        ('Luca', 'Sereni', 'Luca Sereni',
         'luca sereni', '2002-06-21'::date,
         'IT', 'LEFT', 194),

        -- Difensori
        ('Davide', 'Moretti', 'Davide Moretti',
         'davide moretti', '1998-01-18'::date,
         'IT', 'RIGHT', 184),

        ('Matteo', 'Ferri', 'Matteo Ferri',
         'matteo ferri', '1996-09-07'::date,
         'IT', 'RIGHT', 187),

        ('Alessandro', 'Conti', 'Alessandro Conti',
         'alessandro conti', '2000-04-12'::date,
         'IT', 'LEFT', 181),

        ('Simone', 'Leoni', 'Simone Leoni',
         'simone leoni', '1999-12-23'::date,
         'IT', 'RIGHT', 186),

        ('Riccardo', 'Marini', 'Riccardo Marini',
         'riccardo marini', '1995-07-30'::date,
         'IT', 'RIGHT', 189),

        ('Federico', 'Sala', 'Federico Sala',
         'federico sala', '2001-02-16'::date,
         'IT', 'LEFT', 179),

        ('Nicola', 'Gatti', 'Nicola Gatti',
         'nicola gatti', '1997-10-05'::date,
         'IT', 'RIGHT', 185),

        ('Tommaso', 'Villa', 'Tommaso Villa',
         'tommaso villa', '2003-05-28'::date,
         'IT', 'BOTH', 182),

        -- Centrocampisti
        ('Emanuele', 'Greco', 'Emanuele Greco',
         'emanuele greco', '1998-08-11'::date,
         'IT', 'RIGHT', 180),

        ('Gabriele', 'Romano', 'Gabriele Romano',
         'gabriele romano', '1997-01-25'::date,
         'IT', 'LEFT', 177),

        ('Pietro', 'De Luca', 'Pietro De Luca',
         'pietro de luca', '2000-03-09'::date,
         'IT', 'RIGHT', 183),

        ('Samuele', 'Caruso', 'Samuele Caruso',
         'samuele caruso', '1999-06-17'::date,
         'IT', 'BOTH', 178),

        ('Christian', 'Lombardi', 'Christian Lombardi',
         'christian lombardi', '1995-12-04'::date,
         'IT', 'RIGHT', 181),

        ('Filippo', 'Santoro', 'Filippo Santoro',
         'filippo santoro', '2002-09-20'::date,
         'IT', 'LEFT', 176),

        ('Leonardo', 'Martini', 'Leonardo Martini',
         'leonardo martini', '1996-05-13'::date,
         'IT', 'RIGHT', 184),

        -- Attaccanti
        ('Michele', 'Esposito', 'Michele Esposito',
         'michele esposito', '1997-02-08'::date,
         'IT', 'RIGHT', 186),

        ('Antonio', 'Vitale', 'Antonio Vitale',
         'antonio vitale', '1999-10-19'::date,
         'IT', 'LEFT', 181),

        ('Daniele', 'Pellegrini', 'Daniele Pellegrini',
         'daniele pellegrini', '1995-04-26'::date,
         'IT', 'RIGHT', 188),

        ('Kevin', 'Bianchi', 'Kevin Bianchi',
         'kevin bianchi', '2001-07-15'::date,
         'IT', 'BOTH', 179),

        ('Jacopo', 'Colombo', 'Jacopo Colombo',
         'jacopo colombo', '2000-11-29'::date,
         'IT', 'RIGHT', 184)
) AS data(
    first_name,
    last_name,
    display_name,
    normalized_name,
    birth_date,
    nationality_code,
    preferred_foot,
    height_cm
)
WHERE NOT EXISTS (
    SELECT 1
    FROM players existing
    WHERE existing.normalized_name = data.normalized_name
);
-- ------------------------------------------------------------
-- 2. REGISTRAZIONI DEI GIOCATORI NEI CLUB
-- ------------------------------------------------------------

INSERT INTO player_registrations (
    player_id,
    club_id,
    season_id,
    shirt_number,
    joined_at,
    registration_status
)
SELECT
    player.id,
    club.id,
    season.id,
    data.shirt_number,
    '2026-07-01'::date,
    'ACTIVE'
FROM (
    VALUES
        -- Portieri
        ('andrea valenti', 'inter', 1),
        ('marco rinaldi', 'napoli', 12),
        ('luca sereni', 'venezia', 22),

        -- Difensori
        ('davide moretti', 'inter', 2),
        ('matteo ferri', 'napoli', 5),
        ('alessandro conti', 'milan', 3),
        ('simone leoni', 'juventus', 4),
        ('riccardo marini', 'roma', 6),
        ('federico sala', 'atalanta', 23),
        ('nicola gatti', 'lecce', 15),
        ('tommaso villa', 'venezia', 24),

        -- Centrocampisti
        ('emanuele greco', 'inter', 8),
        ('gabriele romano', 'napoli', 10),
        ('pietro de luca', 'milan', 14),
        ('samuele caruso', 'juventus', 16),
        ('christian lombardi', 'roma', 7),
        ('filippo santoro', 'atalanta', 17),
        ('leonardo martini', 'lecce', 21),

        -- Attaccanti
        ('michele esposito', 'inter', 9),
        ('antonio vitale', 'napoli', 11),
        ('daniele pellegrini', 'milan', 19),
        ('kevin bianchi', 'juventus', 18),
        ('jacopo colombo', 'venezia', 20)
) AS data(
    normalized_name,
    club_slug,
    shirt_number
)
JOIN players player
    ON player.normalized_name = data.normalized_name

JOIN clubs club
    ON club.slug = data.club_slug

JOIN seasons season
    ON season.code = '2026/2027'

ON CONFLICT (player_id, season_id)
DO UPDATE SET
    club_id = EXCLUDED.club_id,
    shirt_number = EXCLUDED.shirt_number,
    joined_at = EXCLUDED.joined_at,
    registration_status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;
-- ------------------------------------------------------------
-- 3. RUOLI FANTACALCISTICI
-- Composizione complessiva con i giocatori già esistenti:
-- 3 Portieri - 8 Difensori - 8 Centrocampisti - 6 Attaccanti
-- ------------------------------------------------------------

INSERT INTO player_roles (
    player_id,
    season_id,
    source,
    fantasy_role,
    tactical_role,
    primary_role,
    valid_from
)
SELECT
    player.id,
    season.id,
    'INTERNAL',
    data.fantasy_role,
    data.tactical_role,
    TRUE,
    '2026-07-01'::date
FROM (
    VALUES
        -- Portieri
        ('andrea valenti', 'P', 'Portiere titolare'),
        ('marco rinaldi', 'P', 'Portiere affidabile'),
        ('luca sereni', 'P', 'Portiere giovane di riserva'),

        -- Difensori
        ('davide moretti', 'D', 'Difensore centrale'),
        ('matteo ferri', 'D', 'Difensore centrale'),
        ('alessandro conti', 'D', 'Terzino sinistro offensivo'),
        ('simone leoni', 'D', 'Difensore centrale'),
        ('riccardo marini', 'D', 'Braccetto difensivo'),
        ('federico sala', 'D', 'Esterno a tutta fascia'),
        ('nicola gatti', 'D', 'Difensore marcatore'),
        ('tommaso villa', 'D', 'Terzino destro'),

        -- Centrocampisti
        ('emanuele greco', 'C', 'Regista'),
        ('gabriele romano', 'C', 'Trequartista'),
        ('pietro de luca', 'C', 'Mezzala offensiva'),
        ('samuele caruso', 'C', 'Centrocampista box-to-box'),
        ('christian lombardi', 'C', 'Mediano'),
        ('filippo santoro', 'C', 'Esterno offensivo'),
        ('leonardo martini', 'C', 'Centrocampista centrale'),

        -- Attaccanti
        ('michele esposito', 'A', 'Punta centrale'),
        ('antonio vitale', 'A', 'Seconda punta'),
        ('daniele pellegrini', 'A', 'Centravanti'),
        ('kevin bianchi', 'A', 'Attaccante esterno'),
        ('jacopo colombo', 'A', 'Punta di profondità')
) AS data(
    normalized_name,
    fantasy_role,
    tactical_role
)
JOIN players player
    ON player.normalized_name = data.normalized_name

JOIN seasons season
    ON season.code = '2026/2027'

ON CONFLICT (player_id, season_id, source)
DO UPDATE SET
    fantasy_role = EXCLUDED.fantasy_role,
    tactical_role = EXCLUDED.tactical_role,
    primary_role = TRUE,
    valid_from = EXCLUDED.valid_from;
-- ------------------------------------------------------------
-- 4. VALUTAZIONI DEI GIOCATORI
-- ------------------------------------------------------------

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
    player.id,
    season.id,
    data.projected_fantasy_average,
    data.projected_appearances,
    data.projected_starts,
    data.projected_goals,
    data.projected_assists,
    data.reliability_score,
    data.potential_score,
    data.risk_score,
    data.value_score,
    data.evaluation_summary
FROM (
    VALUES
        -- Portieri
        ('andrea valenti', 5.35, 35, 35, 0, 0, 90, 78, 18, 84,
         'Portiere titolare di alto livello, affidabile e con buona probabilità di clean sheet.'),

        ('marco rinaldi', 5.20, 31, 30, 0, 0, 82, 70, 26, 76,
         'Portiere esperto, rendimento stabile e rischio contenuto.'),

        ('luca sereni', 4.85, 12, 8, 0, 0, 58, 74, 62, 54,
         'Profilo giovane con spazio limitato e maggiore rischio di mancata presenza.'),

        -- Difensori
        ('davide moretti', 6.20, 34, 33, 3, 2, 88, 80, 20, 86,
         'Difensore titolare affidabile, forte sui piazzati e adatto al modificatore.'),

        ('matteo ferri', 6.05, 32, 30, 2, 1, 84, 76, 24, 80,
         'Centrale solido con alta continuità e rischio basso.'),

        ('alessandro conti', 6.35, 33, 29, 3, 6, 78, 86, 32, 88,
         'Terzino offensivo con elevato potenziale assist e buona propensione al bonus.'),

        ('simone leoni', 6.10, 31, 29, 2, 2, 82, 82, 28, 83,
         'Difensore completo, titolare e utile sia in fase difensiva sia sui piazzati.'),

        ('riccardo marini', 5.95, 30, 27, 1, 2, 76, 72, 38, 72,
         'Difensore regolare ma con rischio ammonizioni superiore alla media.'),

        ('federico sala', 6.25, 32, 25, 4, 5, 70, 88, 45, 85,
         'Esterno molto offensivo, alto potenziale bonus ma titolarità meno stabile.'),

        ('nicola gatti', 5.80, 28, 24, 2, 1, 69, 66, 48, 64,
         'Difensore da rotazione con rendimento discreto e rischio medio-alto.'),

        ('tommaso villa', 5.75, 24, 18, 1, 3, 62, 77, 55, 61,
         'Giovane terzino con potenziale, ma ancora poco affidabile nel breve periodo.'),

        -- Centrocampisti
        ('emanuele greco', 6.55, 34, 33, 5, 8, 90, 84, 18, 89,
         'Regista titolare di grande affidabilità, buon contributo assist e continuità.'),

        ('gabriele romano', 7.05, 33, 31, 10, 9, 84, 92, 26, 94,
         'Trequartista premium con forte produzione offensiva e alta probabilità di bonus.'),

        ('pietro de luca', 6.80, 32, 29, 7, 8, 80, 88, 30, 90,
         'Mezzala offensiva completa, ottimo equilibrio tra titolarità e bonus.'),

        ('samuele caruso', 6.35, 33, 30, 4, 6, 86, 80, 22, 84,
         'Centrocampista box-to-box affidabile, continuo e tatticamente centrale.'),

        ('christian lombardi', 5.95, 31, 28, 2, 3, 82, 68, 34, 70,
         'Mediano di equilibrio, affidabile ma con potenziale bonus limitato.'),

        ('filippo santoro', 6.65, 30, 24, 8, 7, 68, 90, 48, 87,
         'Esterno offensivo ad alto potenziale, ma con concorrenza e titolarità variabile.'),

        ('leonardo martini', 6.05, 29, 23, 3, 5, 72, 74, 42, 73,
         'Centrocampista tecnico da rotazione, utile ma non sempre titolare.'),

        -- Attaccanti
        ('michele esposito', 7.35, 34, 33, 19, 6, 88, 94, 22, 96,
         'Centravanti premium, rigorista e riferimento offensivo principale.'),

        ('antonio vitale', 6.95, 32, 29, 14, 8, 80, 89, 30, 91,
         'Seconda punta tecnica con ottima produzione di gol e assist.'),

        ('daniele pellegrini', 6.75, 31, 27, 13, 4, 76, 84, 38, 84,
         'Centravanti affidabile con buon rendimento ma concorrenza moderata.'),

        ('kevin bianchi', 6.60, 30, 23, 10, 7, 68, 91, 50, 86,
         'Attaccante esterno ad alto potenziale, ma con titolarità e continuità variabili.'),

        ('jacopo colombo', 6.10, 27, 18, 8, 3, 60, 82, 58, 68,
         'Punta giovane e rischiosa, utile come scommessa da panchina.')
) AS data(
    normalized_name,
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
JOIN players player
    ON player.normalized_name = data.normalized_name

JOIN seasons season
    ON season.code = '2026/2027'

ON CONFLICT (player_id, season_id)
DO UPDATE SET
    projected_fantasy_average =
        EXCLUDED.projected_fantasy_average,
    projected_appearances =
        EXCLUDED.projected_appearances,
    projected_starts =
        EXCLUDED.projected_starts,
    projected_goals =
        EXCLUDED.projected_goals,
    projected_assists =
        EXCLUDED.projected_assists,
    reliability_score =
        EXCLUDED.reliability_score,
    potential_score =
        EXCLUDED.potential_score,
    risk_score =
        EXCLUDED.risk_score,
    value_score =
        EXCLUDED.value_score,
    evaluation_summary =
        EXCLUDED.evaluation_summary,
    updated_at = CURRENT_TIMESTAMP;
-- ------------------------------------------------------------
-- 5. INSERIMENTO NELLA ROSA ATTIVA
-- Budget iniziale: 1000 crediti
-- Spesa totale prevista: 930 crediti
-- Budget residuo previsto: 70 crediti
-- ------------------------------------------------------------

INSERT INTO fantasy_squad_players (
    squad_id,
    player_id,
    season_id,
    fantasy_role,
    purchase_price
)
SELECT
    squad.id,
    player.id,
    season.id,
    data.fantasy_role,
    data.purchase_price
FROM (
    VALUES
        -- Portieri: totale 63
        ('andrea valenti', 'P', 38),
        ('marco rinaldi', 'P', 20),
        ('luca sereni', 'P', 5),

        -- Difensori: totale 177
        ('davide moretti', 'D', 38),
        ('matteo ferri', 'D', 27),
        ('alessandro conti', 'D', 34),
        ('simone leoni', 'D', 29),
        ('riccardo marini', 'D', 18),
        ('federico sala', 'D', 22),
        ('nicola gatti', 'D', 6),
        ('tommaso villa', 'D', 3),

        -- Centrocampisti: totale 310
        ('emanuele greco', 'C', 48),
        ('gabriele romano', 'C', 82),
        ('pietro de luca', 'C', 58),
        ('samuele caruso', 'C', 42),
        ('christian lombardi', 'C', 22),
        ('filippo santoro', 'C', 38),
        ('leonardo martini', 'C', 12),
        ('giocatore test due', 'C', 8),

        -- Attaccanti: totale 380
        ('michele esposito', 'A', 125),
        ('antonio vitale', 'A', 86),
        ('daniele pellegrini', 'A', 62),
        ('kevin bianchi', 'A', 46),
        ('jacopo colombo', 'A', 18),
        ('giocatore test uno', 'A', 43)
) AS data(
    normalized_name,
    fantasy_role,
    purchase_price
)
JOIN players player
    ON player.normalized_name = data.normalized_name

JOIN seasons season
    ON season.code = '2026/2027'

JOIN fantasy_squads squad
    ON squad.active = TRUE

ON CONFLICT (squad_id, player_id)
DO UPDATE SET
    season_id = EXCLUDED.season_id,
    fantasy_role = EXCLUDED.fantasy_role,
    purchase_price = EXCLUDED.purchase_price;
-- ------------------------------------------------------------
-- 6. DISPONIBILITÀ GIORNATA 1
-- ------------------------------------------------------------

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
    data.status,
    data.starting_probability,
    data.availability_probability,
    data.injury_description,
    data.expected_return_date,
    'MANUAL'
FROM (
    VALUES
        -- Portieri
        ('andrea valenti', 'AVAILABLE', 98, 100, NULL, NULL::date),
        ('marco rinaldi', 'PROBABLE', 82, 96, NULL, NULL::date),
        ('luca sereni', 'AVAILABLE', 20, 100, NULL, NULL::date),

        -- Difensori
        ('davide moretti', 'AVAILABLE', 96, 100, NULL, NULL::date),
        ('matteo ferri', 'PROBABLE', 86, 95, NULL, NULL::date),
        ('alessandro conti', 'AVAILABLE', 91, 100, NULL, NULL::date),
        ('simone leoni', 'AVAILABLE', 93, 100, NULL, NULL::date),
        ('riccardo marini', 'DOUBTFUL', 42, 58,
         'Fastidio muscolare', '2026-08-24'::date),
        ('federico sala', 'PROBABLE', 74, 90, NULL, NULL::date),
        ('nicola gatti', 'SUSPENDED', 0, 0,
         'Squalifica per la giornata 1', NULL::date),
        ('tommaso villa', 'AVAILABLE', 55, 100, NULL, NULL::date),

        -- Centrocampisti
        ('emanuele greco', 'AVAILABLE', 97, 100, NULL, NULL::date),
        ('gabriele romano', 'AVAILABLE', 95, 100, NULL, NULL::date),
        ('pietro de luca', 'PROBABLE', 87, 96, NULL, NULL::date),
        ('samuele caruso', 'AVAILABLE', 92, 100, NULL, NULL::date),
        ('christian lombardi', 'AVAILABLE', 88, 100, NULL, NULL::date),
        ('filippo santoro', 'DOUBTFUL', 48, 62,
         'Affaticamento al flessore', '2026-08-26'::date),
        ('leonardo martini', 'AVAILABLE', 70, 100, NULL, NULL::date),
        ('giocatore test due', 'DOUBTFUL', 35, 55,
         'Affaticamento muscolare', '2026-08-25'::date),

        -- Attaccanti
        ('michele esposito', 'AVAILABLE', 98, 100, NULL, NULL::date),
        ('antonio vitale', 'PROBABLE', 90, 97, NULL, NULL::date),
        ('daniele pellegrini', 'AVAILABLE', 86, 100, NULL, NULL::date),
        ('kevin bianchi', 'DOUBTFUL', 45, 60,
         'Problema alla caviglia', '2026-08-27'::date),
        ('jacopo colombo', 'UNAVAILABLE', 0, 0,
         'Scelta tecnica', NULL::date),
        ('giocatore test uno', 'PROBABLE', 88, 96, NULL, NULL::date)
) AS data(
    normalized_name,
    status,
    starting_probability,
    availability_probability,
    injury_description,
    expected_return_date
)
JOIN players player
    ON player.normalized_name = data.normalized_name

JOIN seasons season
    ON season.code = '2026/2027'

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