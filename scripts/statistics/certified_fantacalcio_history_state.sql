\set ON_ERROR_STOP on
WITH fingerprints(name,value) AS (
 SELECT 'players',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM players t
 UNION ALL SELECT 'clubs',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM clubs t
 UNION ALL SELECT 'registrations',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_registrations t
 UNION ALL SELECT 'roles',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_roles t
 UNION ALL SELECT 'mappings',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_source_mappings t
 UNION ALL SELECT 'profiles',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY player_id),'')) FROM player_identity_profiles t
 UNION ALL SELECT 'provenance',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_identity_provenance t
 UNION ALL SELECT 'statistics',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_season_statistics t
 UNION ALL SELECT 'batches',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_statistics_import_batches t
 UNION ALL SELECT 'import_rows',md5(COALESCE(string_agg(to_jsonb(t)::text,'' ORDER BY id),'')) FROM player_statistics_import_rows t
)
SELECT 'ROWS|'||md5(string_agg(name||'='||value,'|' ORDER BY name)) FROM fingerprints
UNION ALL
SELECT 'SEQUENCES|'||md5(COALESCE(string_agg(sequencename||'='||COALESCE(last_value::text,'NULL'),'|' ORDER BY sequencename),''))
FROM pg_sequences WHERE schemaname='public';
