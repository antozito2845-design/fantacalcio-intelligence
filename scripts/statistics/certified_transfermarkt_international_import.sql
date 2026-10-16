\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE stage_international (
 player_id text,transfermarkt_id text,player_name text,season_code text,competition_code text,
 competition_name text,competition_type text,country text,country_code text,external_club_id text,
 club_name text,appearances text,starts text,minutes_played text,goals text,assists text,
 yellow_cards text,second_yellow_cards text,red_cards text,source text,source_external_id text,
 source_url text,source_page_sha256 text,acquired_at_utc text
) ON COMMIT DROP;
\copy stage_international FROM 'data/statistics/international/transfermarkt-international-statistics.csv' WITH (FORMAT csv,HEADER true)

CREATE TEMP TABLE stage_clubs (
 external_club_id text,club_name text,country text,country_code text,reuse_current_club_name text,resolution_status text
) ON COMMIT DROP;
\copy stage_clubs FROM 'data/statistics/international/transfermarkt-club-resolution.csv' WITH (FORMAT csv,HEADER true)

DO $$ BEGIN
 IF (SELECT count(*) FROM stage_international)<>980 THEN RAISE EXCEPTION 'International row count != 980'; END IF;
 IF (SELECT count(*) FROM stage_clubs)<>183 THEN RAISE EXCEPTION 'International club count != 183'; END IF;
 IF EXISTS (SELECT 1 FROM stage_clubs WHERE resolution_status='REVIEW') THEN RAISE EXCEPTION 'Unresolved club review'; END IF;
 IF EXISTS (SELECT 1 FROM stage_international WHERE source<>'TRANSFERMARKT' OR source_external_id<>transfermarkt_id)
    THEN RAISE EXCEPTION 'Invalid source isolation'; END IF;
 IF EXISTS (SELECT 1 FROM stage_international WHERE season_code NOT IN ('2024/2025','2025/2026'))
    THEN RAISE EXCEPTION 'Invalid season'; END IF;
 IF EXISTS (SELECT 1 FROM stage_international WHERE competition_code='' OR competition_name=''
             OR competition_type NOT IN ('DOMESTIC_LEAGUE','DOMESTIC_CUP','INTERNATIONAL_CUP','OTHER')
             OR country_code !~ '^[A-Z]{2}$') THEN RAISE EXCEPTION 'Invalid competition'; END IF;
 IF EXISTS (SELECT 1 FROM stage_international WHERE
       (appearances<>'' AND appearances::int<0) OR (starts<>'' AND starts::int<0)
       OR (minutes_played<>'' AND minutes_played::int<0) OR (goals<>'' AND goals::int<0)
       OR (assists<>'' AND assists::int<0) OR (yellow_cards<>'' AND yellow_cards::int<0)
       OR (second_yellow_cards<>'' AND second_yellow_cards::int<0) OR (red_cards<>'' AND red_cards::int<0)
       OR (starts<>'' AND appearances<>'' AND starts::int>appearances::int))
    THEN RAISE EXCEPTION 'Invalid negative or inconsistent metrics'; END IF;
 IF EXISTS (SELECT 1 FROM stage_international GROUP BY transfermarkt_id,season_code,competition_code,external_club_id HAVING count(*)<>1)
    THEN RAISE EXCEPTION 'Duplicate staged business key'; END IF;
 IF EXISTS (
   SELECT 1 FROM stage_international s
   LEFT JOIN player_source_mappings m ON m.source='FANTAMARKT'
       AND m.external_id='TRANSFERMARKT-'||s.transfermarkt_id AND m.verified
   LEFT JOIN players p ON p.id=m.player_id
   WHERE p.id IS NULL OR m.id IS NULL
 ) THEN RAISE EXCEPTION 'Identity-before-stats gate failed'; END IF;
 IF EXISTS (
   SELECT 1 FROM stage_international s
   WHERE NOT EXISTS (SELECT 1 FROM player_registrations r JOIN seasons se ON se.id=r.season_id
                     JOIN player_source_mappings m ON m.player_id=r.player_id AND m.source='FANTAMARKT'
                       AND m.external_id='TRANSFERMARKT-'||s.transfermarkt_id AND m.verified
                     WHERE se.code='2026/2027')
 ) THEN RAISE EXCEPTION 'International row is not linked to current catalog'; END IF;
 IF EXISTS (
   SELECT 1 FROM stage_clubs s WHERE s.resolution_status='REUSE_EXISTING_CLUB_EXACT_ALIAS'
   AND (SELECT count(*) FROM clubs c WHERE c.name=s.reuse_current_club_name)<>1
 ) THEN RAISE EXCEPTION 'Existing club alias does not resolve exactly once'; END IF;
END $$;

CREATE TEMP TABLE resolved_existing ON COMMIT DROP AS
SELECT s.*,ps.id statistics_id
FROM stage_international s
JOIN player_source_mappings m ON m.source='FANTAMARKT' AND m.external_id='TRANSFERMARKT-'||s.transfermarkt_id AND m.verified
JOIN seasons se ON se.code=s.season_code
JOIN club_statistical_source_links cl ON cl.source='TRANSFERMARKT' AND cl.external_id=s.external_club_id
JOIN player_competition_season_statistics ps ON ps.player_id=m.player_id AND ps.season_id=se.id
 AND ps.source='TRANSFERMARKT' AND ps.competition_code=s.competition_code AND ps.club_id=cl.club_id;

DO $$ BEGIN
 IF EXISTS (
   SELECT 1 FROM resolved_existing r JOIN player_competition_season_statistics ps ON ps.id=r.statistics_id
   WHERE ps.source_external_id<>r.transfermarkt_id OR ps.competition_name<>r.competition_name
      OR ps.competition_type<>r.competition_type OR ps.country_code<>r.country_code
      OR (ps.appearances IS NOT NULL AND r.appearances<>'' AND ps.appearances<>r.appearances::int)
      OR (ps.starts IS NOT NULL AND r.starts<>'' AND ps.starts<>r.starts::int)
      OR (ps.minutes_played IS NOT NULL AND r.minutes_played<>'' AND ps.minutes_played<>r.minutes_played::int)
      OR (ps.goals IS NOT NULL AND r.goals<>'' AND ps.goals<>r.goals::int)
      OR (ps.assists IS NOT NULL AND r.assists<>'' AND ps.assists<>r.assists::int)
      OR (ps.yellow_cards IS NOT NULL AND r.yellow_cards<>'' AND ps.yellow_cards<>r.yellow_cards::int)
      OR (ps.second_yellow_cards IS NOT NULL AND r.second_yellow_cards<>'' AND ps.second_yellow_cards<>r.second_yellow_cards::int)
      OR (ps.red_cards IS NOT NULL AND r.red_cards<>'' AND ps.red_cards<>r.red_cards::int)
      OR ps.source_page_sha256<>r.source_page_sha256
 ) THEN RAISE EXCEPTION 'Conflict with existing Transfermarkt competition statistics'; END IF;
END $$;

\if :preflight
ROLLBACK;
\echo 'INTERNATIONAL PREFLIGHT SQL: PASS (NO PERSISTENT DML / NO NEXTVAL)'
\quit
\endif

INSERT INTO clubs(name,short_name,slug,country_code,active)
SELECT s.club_name,left(s.club_name,30),'transfermarkt-'||s.external_club_id,s.country_code,FALSE
FROM stage_clubs s
WHERE s.resolution_status='CREATE_INACTIVE_HISTORY'
  AND NOT EXISTS (SELECT 1 FROM club_statistical_source_links l
                  WHERE l.source='TRANSFERMARKT' AND l.external_id=s.external_club_id);

INSERT INTO club_statistical_source_links(club_id,source,external_id,external_name,evidence)
SELECT CASE WHEN s.resolution_status='REUSE_EXISTING_CLUB_EXACT_ALIAS'
            THEN (SELECT c.id FROM clubs c WHERE c.name=s.reuse_current_club_name)
            ELSE (SELECT c.id FROM clubs c WHERE c.slug='transfermarkt-'||s.external_club_id) END,
       'TRANSFERMARKT',s.external_club_id,s.club_name,
       jsonb_build_object('resolution_status',s.resolution_status,'country',s.country,'block',48)
FROM stage_clubs s
WHERE NOT EXISTS (SELECT 1 FROM club_statistical_source_links l
                  WHERE l.source='TRANSFERMARKT' AND l.external_id=s.external_club_id);

CREATE TEMP TABLE batches(season_code text PRIMARY KEY,batch_id bigint) ON COMMIT DROP;
INSERT INTO player_statistics_import_batches(source,season_id,original_filename,status,total_rows,valid_rows,invalid_rows,imported_statistics,completed_at)
SELECT 'TRANSFERMARKT',se.id,'transfermarkt-international-'||replace(s.season_code,'/','-')||'.csv',
       'COMPLETED',count(*),count(*),0,count(*),CURRENT_TIMESTAMP
FROM stage_international s JOIN seasons se ON se.code=s.season_code
GROUP BY se.id,s.season_code
HAVING NOT EXISTS (SELECT 1 FROM player_statistics_import_batches b
                   WHERE b.source='TRANSFERMARKT' AND b.season_id=se.id
                     AND b.original_filename='transfermarkt-international-'||replace(s.season_code,'/','-')||'.csv'
                     AND b.status='COMPLETED');
INSERT INTO batches
SELECT se.code,min(b.id) FROM player_statistics_import_batches b JOIN seasons se ON se.id=b.season_id
WHERE b.source='TRANSFERMARKT' AND b.original_filename LIKE 'transfermarkt-international-%'
GROUP BY se.code;

CREATE TEMP TABLE resolved_stats ON COMMIT DROP AS
SELECT s.*,m.player_id resolved_player_id,se.id season_id,l.club_id,b.batch_id
FROM stage_international s
JOIN player_source_mappings m ON m.source='FANTAMARKT' AND m.external_id='TRANSFERMARKT-'||s.transfermarkt_id AND m.verified
JOIN seasons se ON se.code=s.season_code
JOIN club_statistical_source_links l ON l.source='TRANSFERMARKT' AND l.external_id=s.external_club_id
JOIN batches b ON b.season_code=s.season_code;

UPDATE player_competition_season_statistics ps SET
 appearances=COALESCE(ps.appearances,NULLIF(r.appearances,'')::int),
 starts=COALESCE(ps.starts,NULLIF(r.starts,'')::int),
 minutes_played=COALESCE(ps.minutes_played,NULLIF(r.minutes_played,'')::int),
 goals=COALESCE(ps.goals,NULLIF(r.goals,'')::int), assists=COALESCE(ps.assists,NULLIF(r.assists,'')::int),
 yellow_cards=COALESCE(ps.yellow_cards,NULLIF(r.yellow_cards,'')::int),
 second_yellow_cards=COALESCE(ps.second_yellow_cards,NULLIF(r.second_yellow_cards,'')::int),
 red_cards=COALESCE(ps.red_cards,NULLIF(r.red_cards,'')::int)
FROM resolved_stats r
WHERE ps.player_id=r.resolved_player_id AND ps.season_id=r.season_id AND ps.source='TRANSFERMARKT'
 AND ps.competition_code=r.competition_code AND ps.club_id=r.club_id
 AND ((ps.appearances IS NULL AND r.appearances<>'') OR (ps.starts IS NULL AND r.starts<>'')
   OR (ps.minutes_played IS NULL AND r.minutes_played<>'') OR (ps.goals IS NULL AND r.goals<>'')
   OR (ps.assists IS NULL AND r.assists<>'') OR (ps.yellow_cards IS NULL AND r.yellow_cards<>'')
   OR (ps.second_yellow_cards IS NULL AND r.second_yellow_cards<>'') OR (ps.red_cards IS NULL AND r.red_cards<>''));

INSERT INTO player_competition_season_statistics(
 player_id,club_id,season_id,source,source_external_id,competition_code,competition_name,competition_type,
 country_code,appearances,starts,minutes_played,goals,assists,yellow_cards,second_yellow_cards,red_cards,
 source_url,source_page_sha256,import_batch_id)
SELECT r.resolved_player_id,r.club_id,r.season_id,'TRANSFERMARKT',r.transfermarkt_id,r.competition_code,
 r.competition_name,r.competition_type,r.country_code,NULLIF(r.appearances,'')::int,NULLIF(r.starts,'')::int,
 NULLIF(r.minutes_played,'')::int,NULLIF(r.goals,'')::int,NULLIF(r.assists,'')::int,
 NULLIF(r.yellow_cards,'')::int,NULLIF(r.second_yellow_cards,'')::int,NULLIF(r.red_cards,'')::int,
 r.source_url,r.source_page_sha256,r.batch_id
FROM resolved_stats r
WHERE NOT EXISTS (SELECT 1 FROM player_competition_season_statistics ps
 WHERE ps.player_id=r.resolved_player_id AND ps.season_id=r.season_id AND ps.source='TRANSFERMARKT'
   AND ps.competition_code=r.competition_code AND ps.club_id=r.club_id);

WITH prepared AS (
 SELECT r.*,row_number() OVER(PARTITION BY r.season_code ORDER BY r.transfermarkt_id::bigint,r.competition_code,r.external_club_id) rn
 FROM resolved_stats r
)
INSERT INTO player_statistics_import_rows(batch_id,row_number,external_id,external_name,club_name,
 normalized_name,normalized_club_name,resolved_player_id,resolved_club_id,valid,raw_data)
SELECT p.batch_id,p.rn,p.transfermarkt_id,p.player_name,p.club_name,lower(p.player_name),lower(p.club_name),
 p.resolved_player_id,p.club_id,TRUE,jsonb_build_object(
  'source','TRANSFERMARKT','season',p.season_code,'competition_code',p.competition_code,
  'competition_name',p.competition_name,'competition_type',p.competition_type,'country',p.country,
  'appearances',NULLIF(p.appearances,'')::int,'starts',NULLIF(p.starts,'')::int,
  'minutes_played',NULLIF(p.minutes_played,'')::int,'goals',NULLIF(p.goals,'')::int,
  'assists',NULLIF(p.assists,'')::int,'yellow_cards',NULLIF(p.yellow_cards,'')::int,
  'second_yellow_cards',NULLIF(p.second_yellow_cards,'')::int,'red_cards',NULLIF(p.red_cards,'')::int,
  'source_url',p.source_url,'source_page_sha256',p.source_page_sha256,'block',48)
FROM prepared p
WHERE NOT EXISTS (SELECT 1 FROM player_statistics_import_rows ir WHERE ir.batch_id=p.batch_id AND ir.row_number=p.rn);

DO $$ BEGIN
 IF (SELECT count(*) FROM player_competition_season_statistics WHERE source='TRANSFERMARKT')<>980
    THEN RAISE EXCEPTION 'Final Transfermarkt statistics count != 980'; END IF;
 IF EXISTS (SELECT 1 FROM player_competition_season_statistics GROUP BY player_id,season_id,source,competition_code,club_id HAVING count(*)>1)
    THEN RAISE EXCEPTION 'Duplicate persisted international business key'; END IF;
 IF EXISTS (SELECT 1 FROM clubs c JOIN club_statistical_source_links l ON l.club_id=c.id
            WHERE l.source='TRANSFERMARKT' AND c.slug LIKE 'transfermarkt-%' AND c.active)
    THEN RAISE EXCEPTION 'International historical club became active'; END IF;
END $$;

COMMIT;
\echo 'INTERNATIONAL IMPORT: PASS (COMMIT)'
