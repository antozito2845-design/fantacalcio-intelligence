WITH row_parts AS (
 SELECT 'players' part,md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) value FROM players t
 UNION ALL SELECT 'clubs',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM clubs t
 UNION ALL SELECT 'registrations',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_registrations t
 UNION ALL SELECT 'roles',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_roles t
 UNION ALL SELECT 'mappings',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_source_mappings t
 UNION ALL SELECT 'fanta_stats',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_season_statistics t
 UNION ALL SELECT 'international_stats',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_competition_season_statistics t
 UNION ALL SELECT 'club_links',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM club_statistical_source_links t
 UNION ALL SELECT 'batches',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_statistics_import_batches t
 UNION ALL SELECT 'import_rows',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_statistics_import_rows t
), seq_parts AS (
 SELECT schemaname||'.'||sequencename name,last_value::text value FROM pg_sequences WHERE schemaname='public'
)
SELECT 'ROWS|'||md5(string_agg(part||'='||value,'|' ORDER BY part)) FROM row_parts
UNION ALL
SELECT 'SEQUENCES|'||md5(COALESCE(string_agg(name||'='||value,'|' ORDER BY name),'')) FROM seq_parts;
