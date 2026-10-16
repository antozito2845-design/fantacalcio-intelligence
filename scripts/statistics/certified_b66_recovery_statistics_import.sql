\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE stage_b66_stats (
    player_id text,
    transfermarkt_id text,
    player_name text,
    season_code text,
    competition_code text,
    competition_name text,
    competition_type text,
    country text,
    country_code text,
    external_club_id text,
    club_name text,
    appearances text,
    starts text,
    minutes_played text,
    goals text,
    assists text,
    yellow_cards text,
    second_yellow_cards text,
    red_cards text,
    source text,
    source_external_id text,
    source_url text,
    source_page_sha256 text,
    acquired_at_utc text
) ON COMMIT DROP;

\copy stage_b66_stats FROM 'data/statistics/international/b66-no-history-recovery/b66-recovery-import.csv' WITH (FORMAT csv, HEADER true)

CREATE TEMP TABLE stage_clubs (
    external_club_id text,
    club_name text,
    country text,
    country_code text,
    reuse_current_club_name text,
    resolution_status text
) ON COMMIT DROP;

\copy stage_clubs FROM 'data/statistics/international/transfermarkt-club-resolution.csv' WITH (FORMAT csv, HEADER true)

DO $$
BEGIN
    IF (SELECT count(*) FROM stage_b66_stats) <> 59 THEN
        RAISE EXCEPTION 'B66 recovery rows != 59';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_stats
        WHERE source <> 'TRANSFERMARKT'
           OR source_external_id <> transfermarkt_id
           OR season_code NOT IN ('2024/2025', '2025/2026')
           OR competition_type NOT IN (
                'DOMESTIC_LEAGUE',
                'DOMESTIC_CUP',
                'INTERNATIONAL_CUP',
                'OTHER'
           )
           OR country_code !~ '^[A-Z]{2}$'
           OR source_page_sha256 !~ '^[0-9a-f]{64}$'
           OR source_url = ''
    ) THEN
        RAISE EXCEPTION 'Invalid B66 recovery provenance/contract';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_stats
        GROUP BY
            transfermarkt_id,
            season_code,
            competition_code,
            external_club_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Duplicate B66 recovery business key';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_b66_stats s
        WHERE NOT EXISTS (
            SELECT 1
            FROM player_source_mappings m
            WHERE m.source = 'FANTAMARKT'
              AND m.external_id =
                    'TRANSFERMARKT-' || s.transfermarkt_id
              AND m.player_id = s.player_id::bigint
              AND m.verified
        )
        AND NOT EXISTS (
            SELECT 1
            FROM player_identity_profiles ip
            WHERE ip.player_id = s.player_id::bigint
              AND ip.transfermarkt_player_id =
                    s.transfermarkt_id
        )
    ) THEN
        RAISE EXCEPTION 'B66 verified identity gate failed';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM stage_clubs
        WHERE resolution_status = 'REVIEW'
    ) THEN
        RAISE EXCEPTION 'Unresolved historical club';
    END IF;
END
$$;

INSERT INTO clubs (
    name,
    short_name,
    slug,
    country_code,
    active
)
SELECT
    s.club_name,
    left(s.club_name, 30),
    'transfermarkt-' || s.external_club_id,
    s.country_code,
    FALSE
FROM stage_clubs s
WHERE s.resolution_status = 'CREATE_INACTIVE_HISTORY'
  AND EXISTS (
      SELECT 1
      FROM stage_b66_stats x
      WHERE x.external_club_id = s.external_club_id
  )
  AND NOT EXISTS (
      SELECT 1
      FROM club_statistical_source_links l
      WHERE l.source = 'TRANSFERMARKT'
        AND l.external_id = s.external_club_id
  );

INSERT INTO club_statistical_source_links (
    club_id,
    source,
    external_id,
    external_name,
    evidence
)
SELECT
    CASE
        WHEN s.resolution_status = 'REUSE_EXISTING_CLUB_EXACT_ALIAS'
        THEN (
            SELECT c.id
            FROM clubs c
            WHERE c.name = s.reuse_current_club_name
        )
        ELSE (
            SELECT c.id
            FROM clubs c
            WHERE c.slug = 'transfermarkt-' || s.external_club_id
        )
    END,
    'TRANSFERMARKT',
    s.external_club_id,
    s.club_name,
    jsonb_build_object(
        'resolution_status', s.resolution_status,
        'country', s.country,
        'block', 66,
        'reason', 'B66 no-history recovery'
    )
FROM stage_clubs s
WHERE EXISTS (
    SELECT 1
    FROM stage_b66_stats x
    WHERE x.external_club_id = s.external_club_id
)
AND NOT EXISTS (
    SELECT 1
    FROM club_statistical_source_links l
    WHERE l.source = 'TRANSFERMARKT'
      AND l.external_id = s.external_club_id
);

CREATE TEMP TABLE b66_batches (
    season_code text PRIMARY KEY,
    batch_id bigint
) ON COMMIT DROP;

INSERT INTO player_statistics_import_batches (
    source,
    season_id,
    original_filename,
    status,
    total_rows,
    valid_rows,
    invalid_rows,
    imported_statistics,
    completed_at
)
SELECT
    'TRANSFERMARKT',
    se.id,
    'b66-recovery-transfermarkt-' ||
        replace(s.season_code, '/', '-') ||
        '.csv',
    'COMPLETED',
    count(*),
    count(*),
    0,
    count(*),
    CURRENT_TIMESTAMP
FROM stage_b66_stats s
JOIN seasons se
  ON se.code = s.season_code
GROUP BY se.id, s.season_code
HAVING NOT EXISTS (
    SELECT 1
    FROM player_statistics_import_batches b
    WHERE b.source = 'TRANSFERMARKT'
      AND b.season_id = se.id
      AND b.original_filename =
          'b66-recovery-transfermarkt-' ||
          replace(s.season_code, '/', '-') ||
          '.csv'
      AND b.status = 'COMPLETED'
);

INSERT INTO b66_batches
SELECT
    se.code,
    b.id
FROM player_statistics_import_batches b
JOIN seasons se
  ON se.id = b.season_id
WHERE b.source = 'TRANSFERMARKT'
  AND b.original_filename =
      'b66-recovery-transfermarkt-' ||
      replace(se.code, '/', '-') ||
      '.csv';

CREATE TEMP TABLE resolved_b66_stats
ON COMMIT DROP
AS
SELECT
    s.*,
    s.player_id::bigint AS resolved_player_id,
    se.id AS season_id,
    l.club_id,
    b.batch_id
FROM stage_b66_stats s
JOIN seasons se
  ON se.code = s.season_code
JOIN club_statistical_source_links l
  ON l.source = 'TRANSFERMARKT'
 AND l.external_id = s.external_club_id
JOIN b66_batches b
  ON b.season_code = s.season_code;

DO $$
BEGIN
    IF (SELECT count(*) FROM resolved_b66_stats) <> 59 THEN
        RAISE EXCEPTION 'Resolved B66 statistics != 59';
    END IF;
END
$$;

INSERT INTO player_competition_season_statistics (
    player_id,
    club_id,
    season_id,
    source,
    source_external_id,
    competition_code,
    competition_name,
    competition_type,
    country_code,
    appearances,
    starts,
    minutes_played,
    goals,
    assists,
    yellow_cards,
    second_yellow_cards,
    red_cards,
    source_url,
    source_page_sha256,
    import_batch_id
)
SELECT
    r.resolved_player_id,
    r.club_id,
    r.season_id,
    'TRANSFERMARKT',
    r.transfermarkt_id,
    r.competition_code,
    r.competition_name,
    r.competition_type,
    r.country_code,
    NULLIF(r.appearances, '')::int,
    NULLIF(r.starts, '')::int,
    NULLIF(r.minutes_played, '')::int,
    NULLIF(r.goals, '')::int,
    NULLIF(r.assists, '')::int,
    NULLIF(r.yellow_cards, '')::int,
    NULLIF(r.second_yellow_cards, '')::int,
    NULLIF(r.red_cards, '')::int,
    r.source_url,
    r.source_page_sha256,
    r.batch_id
FROM resolved_b66_stats r
WHERE NOT EXISTS (
    SELECT 1
    FROM player_competition_season_statistics ps
    WHERE ps.player_id = r.resolved_player_id
      AND ps.season_id = r.season_id
      AND ps.source = 'TRANSFERMARKT'
      AND ps.competition_code = r.competition_code
      AND ps.club_id = r.club_id
);

WITH prepared AS (
    SELECT
        r.*,
        row_number() OVER (
            PARTITION BY r.season_code
            ORDER BY
                r.transfermarkt_id::bigint,
                r.competition_code,
                r.external_club_id
        ) AS rn
    FROM resolved_b66_stats r
)
INSERT INTO player_statistics_import_rows (
    batch_id,
    row_number,
    external_id,
    external_name,
    club_name,
    normalized_name,
    normalized_club_name,
    resolved_player_id,
    resolved_club_id,
    valid,
    raw_data
)
SELECT
    p.batch_id,
    p.rn,
    p.transfermarkt_id,
    p.player_name,
    p.club_name,
    lower(p.player_name),
    lower(p.club_name),
    p.resolved_player_id,
    p.club_id,
    TRUE,
    jsonb_build_object(
        'source', 'TRANSFERMARKT',
        'season', p.season_code,
        'competition_code', p.competition_code,
        'competition_name', p.competition_name,
        'competition_type', p.competition_type,
        'country', p.country,
        'appearances', NULLIF(p.appearances, '')::int,
        'starts', NULLIF(p.starts, '')::int,
        'minutes_played', NULLIF(p.minutes_played, '')::int,
        'goals', NULLIF(p.goals, '')::int,
        'assists', NULLIF(p.assists, '')::int,
        'yellow_cards', NULLIF(p.yellow_cards, '')::int,
        'second_yellow_cards', NULLIF(p.second_yellow_cards, '')::int,
        'red_cards', NULLIF(p.red_cards, '')::int,
        'source_url', p.source_url,
        'source_page_sha256', p.source_page_sha256,
        'block', 66,
        'recovery', TRUE
    )
FROM prepared p
WHERE NOT EXISTS (
    SELECT 1
    FROM player_statistics_import_rows ir
    WHERE ir.batch_id = p.batch_id
      AND ir.row_number = p.rn
);

-- Correzione immediata anche nel DB corrente.
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

DO $$
BEGIN
    IF (
        SELECT count(*)
        FROM player_competition_season_statistics
        WHERE source = 'TRANSFERMARKT'
    ) <> 1039 THEN
        RAISE EXCEPTION
            'Final Transfermarkt statistics count != 1039';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM player_competition_season_statistics
        GROUP BY
            player_id,
            season_id,
            source,
            competition_code,
            club_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Duplicate persisted B66 business key';
    END IF;

    IF (
        SELECT count(DISTINCT player_id)
        FROM player_competition_season_statistics
        WHERE source = 'TRANSFERMARKT'
          AND player_id IN (
              1032,1033,1034,1035,
              1036,1037,1038,1111
          )
    ) <> 8 THEN
        RAISE EXCEPTION
            'Not all 8 recovered players persisted';
    END IF;
END
$$;

COMMIT;

\echo 'B66 RECOVERY STATISTICS IMPORT: PASS'