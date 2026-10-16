\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE stage_profiles (
  external_id text, source_name text, profile_full_name text, birth_date text,
  club text, fantasy_role text, nationality text, height text, profile_url text,
  profile_sha256 text, profile_status text, collision_player_id text, notes text,
  canonical_full_name text, first_name text, last_name text
) ON COMMIT DROP;
CREATE TEMP TABLE stage_stats (
  season_code text, external_id text, name text, club text, historical_role text,
  appearances text, starts text, minutes_played text, average_rating text,
  fantasy_average text, goals text, assists text, penalties_taken text,
  penalties_scored text, penalties_missed text, yellow_cards text, red_cards text,
  own_goals text, goals_conceded text, clean_sheets text, saves text,
  penalties_saved text, expected_goals text, expected_assists text, source_url text
) ON COMMIT DROP;

\copy stage_profiles FROM 'data/statistics/fantacalcio/historical-fantacalcio-profiles.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
CREATE TEMP TABLE stage_24 (LIKE stage_stats) ON COMMIT DROP;
CREATE TEMP TABLE stage_25 (LIKE stage_stats) ON COMMIT DROP;
\copy stage_24 (external_id,name,club,historical_role,appearances,starts,minutes_played,average_rating,fantasy_average,goals,assists,penalties_taken,penalties_scored,penalties_missed,yellow_cards,red_cards,own_goals,goals_conceded,clean_sheets,saves,penalties_saved,expected_goals,expected_assists,source_url) FROM 'data/statistics/fantacalcio/fantacalcio-serie-a-2024-25.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy stage_25 (external_id,name,club,historical_role,appearances,starts,minutes_played,average_rating,fantasy_average,goals,assists,penalties_taken,penalties_scored,penalties_missed,yellow_cards,red_cards,own_goals,goals_conceded,clean_sheets,saves,penalties_saved,expected_goals,expected_assists,source_url) FROM 'data/statistics/fantacalcio/fantacalcio-serie-a-2025-26.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
UPDATE stage_24 SET season_code='2024/2025';
UPDATE stage_25 SET season_code='2025/2026';
INSERT INTO stage_stats SELECT * FROM stage_24;
INSERT INTO stage_stats SELECT * FROM stage_25;

-- Read-only dataset and identity gates. They execute in both modes before writes.
DO $$
BEGIN
  IF (SELECT count(*) FROM stage_profiles WHERE profile_status='VERIFIED') <> 485
     OR (SELECT count(*) FROM stage_profiles) <> 485 THEN
    RAISE EXCEPTION 'Expected exactly 485 verified historical profiles';
  END IF;
  IF (SELECT count(*) FROM stage_stats WHERE season_code='2024/2025') <> 679
     OR (SELECT count(*) FROM stage_stats WHERE season_code='2025/2026') <> 663 THEN
    RAISE EXCEPTION 'Unexpected Fantacalcio source cardinality';
  END IF;
  IF EXISTS (SELECT 1 FROM stage_stats GROUP BY season_code,external_id HAVING count(*)>1)
     OR EXISTS (SELECT 1 FROM stage_profiles GROUP BY external_id HAVING count(*)>1) THEN
    RAISE EXCEPTION 'Duplicate source identity/business key';
  END IF;
  IF EXISTS (SELECT 1 FROM stage_profiles WHERE external_id !~ '^FANTACALCIO-[0-9]+$'
             OR birth_date::date IS NULL OR canonical_full_name='' OR last_name='') THEN
    RAISE EXCEPTION 'Invalid certified historical profile';
  END IF;
  IF EXISTS (SELECT 1 FROM stage_stats WHERE historical_role NOT IN ('P','D','C','A')) THEN
    RAISE EXCEPTION 'Invalid historical fantasy role';
  END IF;
  IF EXISTS (
    SELECT 1 FROM stage_profiles p
    JOIN player_source_mappings m ON m.source='FANTACALCIO' AND m.external_id=p.external_id
    GROUP BY p.external_id HAVING count(DISTINCT m.player_id)<>1
  ) THEN RAISE EXCEPTION 'Fantacalcio external ID mapping collision'; END IF;
  IF EXISTS (
    SELECT 1 FROM stage_profiles p
    LEFT JOIN player_source_mappings m ON m.source='FANTACALCIO' AND m.external_id=p.external_id
    JOIN players existing ON existing.display_name=p.canonical_full_name
                         AND existing.birth_date=p.birth_date::date
                         AND existing.id<>COALESCE(m.player_id,-1)
    WHERE m.player_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM player_source_mappings em
                      WHERE em.player_id=existing.id AND em.source='FANTACALCIO'
                        AND em.external_id=p.external_id)
  ) THEN RAISE EXCEPTION 'Potential historical identity collision requires REVIEW'; END IF;
END $$;

CREATE TEMP TABLE resolved_existing_stats ON COMMIT DROP AS
SELECT s.*, m.player_id, se.id AS season_id, c.id AS club_id
FROM stage_stats s
JOIN player_source_mappings m ON m.source='FANTACALCIO' AND m.external_id=s.external_id AND m.verified
JOIN seasons se ON se.code=s.season_code
LEFT JOIN clubs c ON lower(c.name)=lower(s.club);

-- Complete non-null conflict gate: incoming NULL never conflicts and never deletes.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM resolved_existing_stats WHERE club_id IS NULL
             AND club NOT IN ('Cremonese','Empoli','Pisa','Verona')) THEN
    RAISE EXCEPTION 'Historical club unresolved';
  END IF;
  IF EXISTS (
    SELECT 1 FROM player_season_statistics old
    JOIN resolved_existing_stats incoming
      ON old.player_id=incoming.player_id AND old.season_id=incoming.season_id
     AND old.source='FANTACALCIO'
    WHERE (old.club_id IS NOT NULL AND incoming.club_id IS NOT NULL AND old.club_id<>incoming.club_id)
       OR (old.source_external_id IS NOT NULL AND incoming.external_id IS NOT NULL AND old.source_external_id<>incoming.external_id)
       OR (old.appearances IS NOT NULL AND NULLIF(incoming.appearances,'') IS NOT NULL AND old.appearances<>incoming.appearances::int)
       OR (old.starts IS NOT NULL AND NULLIF(incoming.starts,'') IS NOT NULL AND old.starts<>incoming.starts::int)
       OR (old.minutes_played IS NOT NULL AND NULLIF(incoming.minutes_played,'') IS NOT NULL AND old.minutes_played<>incoming.minutes_played::int)
       OR (old.average_rating IS NOT NULL AND NULLIF(incoming.average_rating,'') IS NOT NULL AND old.average_rating<>incoming.average_rating::numeric)
       OR (old.fantasy_average IS NOT NULL AND NULLIF(incoming.fantasy_average,'') IS NOT NULL AND old.fantasy_average<>incoming.fantasy_average::numeric)
       OR (old.goals IS NOT NULL AND NULLIF(incoming.goals,'') IS NOT NULL AND old.goals<>incoming.goals::int)
       OR (old.assists IS NOT NULL AND NULLIF(incoming.assists,'') IS NOT NULL AND old.assists<>incoming.assists::int)
       OR (old.penalties_scored IS NOT NULL AND NULLIF(incoming.penalties_scored,'') IS NOT NULL AND old.penalties_scored<>incoming.penalties_scored::int)
       OR (old.penalties_missed IS NOT NULL AND NULLIF(incoming.penalties_missed,'') IS NOT NULL AND old.penalties_missed<>incoming.penalties_missed::int)
       OR (old.penalties_taken IS NOT NULL AND NULLIF(incoming.penalties_taken,'') IS NOT NULL AND old.penalties_taken<>incoming.penalties_taken::int)
       OR (old.yellow_cards IS NOT NULL AND NULLIF(incoming.yellow_cards,'') IS NOT NULL AND old.yellow_cards<>incoming.yellow_cards::int)
       OR (old.red_cards IS NOT NULL AND NULLIF(incoming.red_cards,'') IS NOT NULL AND old.red_cards<>incoming.red_cards::int)
       OR (old.own_goals IS NOT NULL AND NULLIF(incoming.own_goals,'') IS NOT NULL AND old.own_goals<>incoming.own_goals::int)
       OR (old.goals_conceded IS NOT NULL AND NULLIF(incoming.goals_conceded,'') IS NOT NULL AND old.goals_conceded<>incoming.goals_conceded::int)
       OR (old.clean_sheets IS NOT NULL AND NULLIF(incoming.clean_sheets,'') IS NOT NULL AND old.clean_sheets<>incoming.clean_sheets::int)
       OR (old.saves IS NOT NULL AND NULLIF(incoming.saves,'') IS NOT NULL AND old.saves<>incoming.saves::int)
       OR (old.penalties_saved IS NOT NULL AND NULLIF(incoming.penalties_saved,'') IS NOT NULL AND old.penalties_saved<>incoming.penalties_saved::int)
       OR (old.expected_goals IS NOT NULL AND NULLIF(incoming.expected_goals,'') IS NOT NULL AND old.expected_goals<>incoming.expected_goals::numeric)
       OR (old.expected_assists IS NOT NULL AND NULLIF(incoming.expected_assists,'') IS NOT NULL AND old.expected_assists<>incoming.expected_assists::numeric)
  ) THEN RAISE EXCEPTION 'Conflict with existing certified statistics'; END IF;
  IF EXISTS (
    SELECT 1 FROM player_roles old
    JOIN resolved_existing_stats incoming
      ON old.player_id=incoming.player_id AND old.season_id=incoming.season_id
     AND old.source='FANTACALCIO'
    WHERE old.fantasy_role<>incoming.historical_role
  ) THEN RAISE EXCEPTION 'Conflict with existing historical Fantacalcio role'; END IF;
END $$;

\if :preflight
ROLLBACK;
\echo 'PREFLIGHT SQL: PASS (NO PERSISTENT DML / NO NEXTVAL)'
\quit
\endif

-- APPLY starts only after all read-only gates have passed.
INSERT INTO clubs(name,short_name,slug,country_code,active)
SELECT value,value,lower(value),'IT',FALSE
FROM (VALUES ('Cremonese'),('Empoli'),('Pisa'),('Verona')) v(value)
WHERE NOT EXISTS (SELECT 1 FROM clubs c WHERE c.slug=lower(v.value));

-- Direct external-ID -> generated player-ID assignment. No name/DOB re-association.
CREATE TEMP TABLE historical_identity_assignment (
  external_id text PRIMARY KEY,
  player_id bigint NOT NULL UNIQUE,
  created_now boolean NOT NULL,
  canonical_full_name text NOT NULL,
  first_name text,
  last_name text NOT NULL,
  birth_date date NOT NULL,
  height_cm smallint,
  profile_full_name text NOT NULL,
  profile_url text NOT NULL,
  profile_sha256 text NOT NULL
) ON COMMIT DROP;

INSERT INTO historical_identity_assignment
SELECT p.external_id,m.player_id,FALSE,p.canonical_full_name,NULLIF(p.first_name,''),p.last_name,
       p.birth_date::date,CASE WHEN p.height~'^[0-9]{3}cm$' THEN replace(p.height,'cm','')::smallint END,
       p.profile_full_name,p.profile_url,p.profile_sha256
FROM stage_profiles p
JOIN player_source_mappings m ON m.source='FANTACALCIO' AND m.external_id=p.external_id AND m.verified;

INSERT INTO historical_identity_assignment
SELECT p.external_id,nextval(pg_get_serial_sequence('players','id')),TRUE,
       p.canonical_full_name,NULLIF(p.first_name,''),p.last_name,p.birth_date::date,
       CASE WHEN p.height~'^[0-9]{3}cm$' THEN replace(p.height,'cm','')::smallint END,
       p.profile_full_name,p.profile_url,p.profile_sha256
FROM stage_profiles p
WHERE NOT EXISTS (SELECT 1 FROM historical_identity_assignment a WHERE a.external_id=p.external_id)
ORDER BY p.external_id;

DO $$ BEGIN
  IF (SELECT count(*) FROM historical_identity_assignment)<>485
     OR EXISTS (SELECT 1 FROM historical_identity_assignment GROUP BY external_id HAVING count(*)<>1)
     OR EXISTS (SELECT 1 FROM historical_identity_assignment GROUP BY player_id HAVING count(*)<>1) THEN
    RAISE EXCEPTION 'Deterministic historical identity assignment failed';
  END IF;
END $$;

INSERT INTO players(id,first_name,last_name,display_name,normalized_name,birth_date,height_cm,active)
SELECT a.player_id,a.first_name,a.last_name,a.canonical_full_name,
       lower(regexp_replace(a.canonical_full_name,'[^[:alnum:] ]','','g')),
       a.birth_date,a.height_cm,FALSE
FROM historical_identity_assignment a WHERE a.created_now;

INSERT INTO player_source_mappings(player_id,source,external_id,external_name,external_url,verified)
SELECT player_id,'FANTACALCIO',external_id,profile_full_name,profile_url,TRUE
FROM historical_identity_assignment a
WHERE NOT EXISTS (SELECT 1 FROM player_source_mappings m
                  WHERE m.source='FANTACALCIO' AND m.external_id=a.external_id);

INSERT INTO player_identity_profiles(player_id,canonical_full_name,first_name,last_name,birth_date,
                                     height_cm,identity_status,confidence)
SELECT player_id,canonical_full_name,first_name,last_name,birth_date,height_cm,'VERIFIED_PARTIAL','HIGH'
FROM historical_identity_assignment a
WHERE NOT EXISTS (SELECT 1 FROM player_identity_profiles p WHERE p.player_id=a.player_id);

INSERT INTO player_identity_provenance(player_id,field_name,source,source_external_id,source_url,confidence,evidence)
SELECT a.player_id,f.field_name,'FANTACALCIO',a.external_id,a.profile_url,'HIGH',
       jsonb_build_object('profile_sha256',a.profile_sha256,'value',f.field_value,'block',48)
FROM historical_identity_assignment a
CROSS JOIN LATERAL (VALUES
 ('CANONICAL_FULL_NAME',a.canonical_full_name),('BIRTH_DATE',a.birth_date::text),('IDENTITY_STATUS','VERIFIED_PARTIAL')
) f(field_name,field_value)
WHERE NOT EXISTS (SELECT 1 FROM player_identity_provenance p
                  WHERE p.player_id=a.player_id AND p.field_name=f.field_name
                    AND p.source='FANTACALCIO' AND p.source_external_id=a.external_id);

CREATE TEMP TABLE resolved_stats ON COMMIT DROP AS
SELECT s.*,m.player_id,se.id season_id,c.id club_id
FROM stage_stats s
JOIN player_source_mappings m ON m.source='FANTACALCIO' AND m.external_id=s.external_id AND m.verified
JOIN seasons se ON se.code=s.season_code
JOIN clubs c ON lower(c.name)=lower(s.club);
DO $$ BEGIN
 IF (SELECT count(*) FROM resolved_stats)<>1342 THEN RAISE EXCEPTION 'Not all rows resolved after assignment'; END IF;
END $$;

INSERT INTO player_roles(player_id,season_id,source,fantasy_role,primary_role)
SELECT DISTINCT r.player_id,r.season_id,'FANTACALCIO',r.historical_role,TRUE FROM resolved_stats r
WHERE NOT EXISTS (SELECT 1 FROM player_roles pr
                  WHERE pr.player_id=r.player_id AND pr.season_id=r.season_id AND pr.source='FANTACALCIO');

CREATE TEMP TABLE batches(season_code text PRIMARY KEY,batch_id bigint) ON COMMIT DROP;
INSERT INTO player_statistics_import_batches(source,season_id,original_filename,status,total_rows,valid_rows,invalid_rows,imported_statistics,completed_at)
SELECT 'FANTACALCIO',s.id,v.filename,'COMPLETED',v.rows,v.rows,0,v.rows,CURRENT_TIMESTAMP
FROM (VALUES ('2024/2025','fantacalcio-serie-a-2024-25.csv',679),
             ('2025/2026','fantacalcio-serie-a-2025-26.csv',663)) v(code,filename,rows)
JOIN seasons s ON s.code=v.code
WHERE NOT EXISTS (SELECT 1 FROM player_statistics_import_batches b
                  WHERE b.source='FANTACALCIO' AND b.season_id=s.id AND b.original_filename=v.filename AND b.status='COMPLETED');
INSERT INTO batches
SELECT s.code,min(b.id) FROM player_statistics_import_batches b JOIN seasons s ON s.id=b.season_id
WHERE b.source='FANTACALCIO' AND b.original_filename IN ('fantacalcio-serie-a-2024-25.csv','fantacalcio-serie-a-2025-26.csv')
GROUP BY s.code;

UPDATE player_season_statistics ps SET
 club_id=COALESCE(ps.club_id,r.club_id),
 source_external_id=COALESCE(ps.source_external_id,r.external_id),
 appearances=COALESCE(ps.appearances,NULLIF(r.appearances,'')::int),
 starts=COALESCE(ps.starts,NULLIF(r.starts,'')::int),
 minutes_played=COALESCE(ps.minutes_played,NULLIF(r.minutes_played,'')::int),
 average_rating=COALESCE(ps.average_rating,NULLIF(r.average_rating,'')::numeric),
 fantasy_average=COALESCE(ps.fantasy_average,NULLIF(r.fantasy_average,'')::numeric),
 goals=COALESCE(ps.goals,NULLIF(r.goals,'')::int),
 assists=COALESCE(ps.assists,NULLIF(r.assists,'')::int),
 penalties_scored=COALESCE(ps.penalties_scored,NULLIF(r.penalties_scored,'')::int),
 penalties_missed=COALESCE(ps.penalties_missed,NULLIF(r.penalties_missed,'')::int),
 penalties_taken=COALESCE(ps.penalties_taken,NULLIF(r.penalties_taken,'')::int),
 yellow_cards=COALESCE(ps.yellow_cards,NULLIF(r.yellow_cards,'')::int),
 red_cards=COALESCE(ps.red_cards,NULLIF(r.red_cards,'')::int),
 own_goals=COALESCE(ps.own_goals,NULLIF(r.own_goals,'')::int),
 goals_conceded=COALESCE(ps.goals_conceded,NULLIF(r.goals_conceded,'')::int),
 clean_sheets=COALESCE(ps.clean_sheets,NULLIF(r.clean_sheets,'')::int),
 saves=COALESCE(ps.saves,NULLIF(r.saves,'')::int),
 penalties_saved=COALESCE(ps.penalties_saved,NULLIF(r.penalties_saved,'')::int),
 expected_goals=COALESCE(ps.expected_goals,NULLIF(r.expected_goals,'')::numeric),
 expected_assists=COALESCE(ps.expected_assists,NULLIF(r.expected_assists,'')::numeric),
 import_batch_id=COALESCE(ps.import_batch_id,b.batch_id)
FROM resolved_stats r JOIN batches b ON b.season_code=r.season_code
WHERE ps.player_id=r.player_id AND ps.season_id=r.season_id AND ps.source='FANTACALCIO'
  AND (ps.club_id IS NULL OR ps.source_external_id IS NULL
       OR (ps.appearances IS NULL AND NULLIF(r.appearances,'') IS NOT NULL)
       OR (ps.starts IS NULL AND NULLIF(r.starts,'') IS NOT NULL)
       OR (ps.minutes_played IS NULL AND NULLIF(r.minutes_played,'') IS NOT NULL)
       OR (ps.average_rating IS NULL AND NULLIF(r.average_rating,'') IS NOT NULL)
       OR (ps.fantasy_average IS NULL AND NULLIF(r.fantasy_average,'') IS NOT NULL)
       OR (ps.goals IS NULL AND NULLIF(r.goals,'') IS NOT NULL)
       OR (ps.assists IS NULL AND NULLIF(r.assists,'') IS NOT NULL)
       OR (ps.penalties_scored IS NULL AND NULLIF(r.penalties_scored,'') IS NOT NULL)
       OR (ps.penalties_missed IS NULL AND NULLIF(r.penalties_missed,'') IS NOT NULL)
       OR (ps.penalties_taken IS NULL AND NULLIF(r.penalties_taken,'') IS NOT NULL)
       OR (ps.yellow_cards IS NULL AND NULLIF(r.yellow_cards,'') IS NOT NULL)
       OR (ps.red_cards IS NULL AND NULLIF(r.red_cards,'') IS NOT NULL)
       OR (ps.own_goals IS NULL AND NULLIF(r.own_goals,'') IS NOT NULL)
       OR (ps.goals_conceded IS NULL AND NULLIF(r.goals_conceded,'') IS NOT NULL)
       OR (ps.clean_sheets IS NULL AND NULLIF(r.clean_sheets,'') IS NOT NULL)
       OR (ps.saves IS NULL AND NULLIF(r.saves,'') IS NOT NULL)
       OR (ps.penalties_saved IS NULL AND NULLIF(r.penalties_saved,'') IS NOT NULL)
       OR (ps.expected_goals IS NULL AND NULLIF(r.expected_goals,'') IS NOT NULL)
       OR (ps.expected_assists IS NULL AND NULLIF(r.expected_assists,'') IS NOT NULL)
       OR ps.import_batch_id IS NULL);

INSERT INTO player_season_statistics(player_id,club_id,season_id,source,source_external_id,
 appearances,starts,minutes_played,average_rating,fantasy_average,goals,assists,penalties_scored,
 penalties_missed,penalties_taken,yellow_cards,red_cards,own_goals,goals_conceded,clean_sheets,
 saves,penalties_saved,expected_goals,expected_assists,import_batch_id)
SELECT r.player_id,r.club_id,r.season_id,'FANTACALCIO',r.external_id,
 NULLIF(r.appearances,'')::int,NULLIF(r.starts,'')::int,NULLIF(r.minutes_played,'')::int,
 NULLIF(r.average_rating,'')::numeric,NULLIF(r.fantasy_average,'')::numeric,
 NULLIF(r.goals,'')::int,NULLIF(r.assists,'')::int,NULLIF(r.penalties_scored,'')::int,
 NULLIF(r.penalties_missed,'')::int,NULLIF(r.penalties_taken,'')::int,NULLIF(r.yellow_cards,'')::int,
 NULLIF(r.red_cards,'')::int,NULLIF(r.own_goals,'')::int,NULLIF(r.goals_conceded,'')::int,
 NULLIF(r.clean_sheets,'')::int,NULLIF(r.saves,'')::int,NULLIF(r.penalties_saved,'')::int,
 NULLIF(r.expected_goals,'')::numeric,NULLIF(r.expected_assists,'')::numeric,b.batch_id
FROM resolved_stats r JOIN batches b ON b.season_code=r.season_code
WHERE NOT EXISTS (SELECT 1 FROM player_season_statistics ps
                  WHERE ps.player_id=r.player_id AND ps.season_id=r.season_id AND ps.source='FANTACALCIO');

WITH prepared_import_rows AS (
 SELECT b.batch_id,row_number() OVER(PARTITION BY r.season_code ORDER BY r.external_id) AS row_number,
        r.external_id,r.name,r.club,r.player_id,r.club_id,r.source_url,r.historical_role
 FROM resolved_stats r JOIN batches b ON b.season_code=r.season_code
)
INSERT INTO player_statistics_import_rows(batch_id,row_number,external_id,external_name,normalized_name,
 club_name,normalized_club_name,resolved_player_id,resolved_club_id,valid,raw_data)
SELECT p.batch_id,p.row_number,p.external_id,p.name,lower(p.name),p.club,lower(p.club),p.player_id,p.club_id,TRUE,
       jsonb_build_object('source_url',p.source_url,'historical_role',p.historical_role,'block',48)
FROM prepared_import_rows p
WHERE NOT EXISTS (SELECT 1 FROM player_statistics_import_rows ir
                  WHERE ir.batch_id=p.batch_id AND ir.row_number=p.row_number);

DO $$ BEGIN
 IF (SELECT count(*) FROM player_season_statistics ps JOIN seasons s ON s.id=ps.season_id
     WHERE ps.source='FANTACALCIO' AND s.code IN ('2024/2025','2025/2026'))<>1342 THEN
   RAISE EXCEPTION 'Final Fantacalcio stats cardinality != 1342'; END IF;
 IF EXISTS (SELECT 1 FROM historical_identity_assignment a JOIN players p ON p.id=a.player_id WHERE p.active) THEN
   RAISE EXCEPTION 'Historical player became active'; END IF;
 IF EXISTS (SELECT 1 FROM historical_identity_assignment a JOIN player_registrations r ON r.player_id=a.player_id
            JOIN seasons s ON s.id=r.season_id WHERE s.code='2026/2027') THEN
   RAISE EXCEPTION 'Historical player received current registration'; END IF;
 IF EXISTS (SELECT 1 FROM player_roles old JOIN resolved_stats incoming
            ON old.player_id=incoming.player_id AND old.season_id=incoming.season_id AND old.source='FANTACALCIO'
            WHERE old.fantasy_role<>incoming.historical_role) THEN
   RAISE EXCEPTION 'Post-apply historical role conflict'; END IF;
END $$;
COMMIT;
