\set ON_ERROR_STOP on
\pset pager off

BEGIN;

CREATE TEMP TABLE certified_identity (
    status text, match_rule text, fantacalcio_player_id text,
    fantacalcio_short_name text, fantacalcio_full_name text,
    fantacalcio_club text, fantasy_role text, fantacalcio_dob text,
    fantacalcio_height text, fantacalcio_nationality text,
    fantacalcio_foot text, fantacalcio_url text,
    transfermarkt_player_id text, transfermarkt_full_name text,
    transfermarkt_team text, transfermarkt_dob text,
    transfermarkt_height text, transfermarkt_nationality text,
    transfermarkt_url text, name_exact text, dob_match text,
    club_match text, height_match text, nationality_overlap text,
    candidate_count text, notes text, season_status text,
    catalog_visibility text, ai_eligible text, eligibility_reason text,
    canonical_date_of_birth text, dob_resolution_status text,
    dob_resolution_source text, dob_resolution_url text
);

CREATE TEMP TABLE certified_reconciliation (
    category text, rule text, fantacalcio_player_id text,
    fantacalcio_short_name text, fantacalcio_full_name text,
    fantacalcio_club text, fantasy_role text,
    canonical_date_of_birth text, transfermarkt_player_id text,
    season_status text, catalog_visibility text, ai_eligible text,
    existing_player_id text, existing_catalog_name text,
    existing_catalog_club text, existing_fantasy_role text,
    existing_transfermarkt_player_id text, existing_date_of_birth text,
    notes text
);

\copy certified_identity FROM 'data/identity/comparison-2026-2027/fantacalcio-transfermarkt-certified-final.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')
\copy certified_reconciliation FROM 'data/identity/comparison-2026-2027/catalog-reconciliation-certified-final.csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

CREATE FUNCTION pg_temp.assert_eq(actual bigint, expected bigint, label text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF actual IS DISTINCT FROM expected THEN
        RAISE EXCEPTION 'ASSERTION FAILED: % (actual %, expected %)', label, actual, expected;
    END IF;
END $$;

CREATE FUNCTION pg_temp.assert_zero(actual bigint, label text)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF actual <> 0 THEN
        RAISE EXCEPTION 'ASSERTION FAILED: % (actual %, expected 0)', label, actual;
    END IF;
END $$;

SELECT pg_temp.assert_eq(COUNT(*), 497, 'certified identity rows') FROM certified_identity;
SELECT pg_temp.assert_eq(COUNT(*), 495, 'certified visible reconciliation rows') FROM certified_reconciliation;
SELECT pg_temp.assert_zero(COUNT(*), 'identity review/unmatched rows')
FROM certified_identity WHERE status NOT IN ('HIGH', 'ALIAS_HIGH', 'INACTIVE_VERIFIED');
SELECT pg_temp.assert_zero(COUNT(*), 'duplicate Fantacalcio ids') FROM (
    SELECT fantacalcio_player_id FROM certified_identity GROUP BY 1 HAVING COUNT(*) > 1
) duplicates;
SELECT pg_temp.assert_zero(COUNT(*), 'duplicate Transfermarkt ids') FROM (
    SELECT transfermarkt_player_id FROM certified_identity
    WHERE NULLIF(transfermarkt_player_id, '') IS NOT NULL
    GROUP BY 1 HAVING COUNT(*) > 1
) duplicates;
SELECT pg_temp.assert_zero(COUNT(*), 'invalid fantasy roles')
FROM certified_identity WHERE fantasy_role NOT IN ('P','D','C','A');
SELECT pg_temp.assert_zero(COUNT(*), 'missing club mappings')
FROM certified_identity ci LEFT JOIN clubs c ON lower(c.name)=lower(ci.fantacalcio_club)
WHERE c.id IS NULL;
SELECT pg_temp.assert_zero(COUNT(*), 'existing reconciliation player missing')
FROM certified_reconciliation cr LEFT JOIN players p ON p.id=NULLIF(cr.existing_player_id,'')::bigint
WHERE cr.category <> 'NEW_PLAYER' AND p.id IS NULL;
SELECT pg_temp.assert_zero(COUNT(*), 'certified existing player collision')
FROM certified_reconciliation cr
JOIN player_source_mappings m ON m.source='FANTACALCIO'
 AND m.external_id='FANTACALCIO-' || cr.fantacalcio_player_id
WHERE NULLIF(cr.existing_player_id,'') IS NOT NULL
  AND m.player_id <> cr.existing_player_id::bigint;
SELECT pg_temp.assert_eq(COUNT(*), 1, 'Maldini id258 match') FROM certified_reconciliation
WHERE fantacalcio_player_id='4896' AND existing_player_id='258' AND fantacalcio_club='Cagliari';
SELECT pg_temp.assert_eq(COUNT(*), 1, 'Russo id510 identity') FROM certified_identity ci
JOIN player_source_mappings m ON m.source='FANTACALCIO'
 AND m.external_id='FANTACALCIO-' || ci.fantacalcio_player_id
WHERE ci.fantacalcio_player_id='4518' AND m.player_id=510 AND ci.season_status='LOAN_OUT';
SELECT pg_temp.assert_eq(COUNT(*), 1, 'Satalino hidden released') FROM certified_identity
WHERE fantacalcio_player_id='2127' AND season_status='FREE_AGENT' AND catalog_visibility='HIDDEN';
SELECT pg_temp.assert_eq(COUNT(*), 1, 'Vogliacco preserved outside certified current')
FROM players WHERE id=533 AND display_name='Vogliacco';

SELECT pg_temp.assert_zero(
    COUNT(*),
    'existing identity profile certified conflict'
)
FROM certified_identity ci
JOIN player_source_mappings m
  ON m.source = 'FANTACALCIO'
 AND m.external_id = 'FANTACALCIO-' || ci.fantacalcio_player_id
JOIN player_identity_profiles pip
  ON pip.player_id = m.player_id
WHERE
       (
           pip.canonical_full_name IS NOT NULL
           AND NULLIF(ci.fantacalcio_full_name, '') IS NOT NULL
           AND pip.canonical_full_name IS DISTINCT FROM ci.fantacalcio_full_name
       )
    OR (
           pip.birth_date IS NOT NULL
           AND NULLIF(ci.canonical_date_of_birth, '') IS NOT NULL
           AND pip.birth_date IS DISTINCT FROM ci.canonical_date_of_birth::date
       )
    OR (
           pip.nationality IS NOT NULL
           AND NULLIF(ci.fantacalcio_nationality, '') IS NOT NULL
           AND pip.nationality IS DISTINCT FROM ci.fantacalcio_nationality
       )
    OR (
           pip.height_cm IS NOT NULL
           AND NULLIF(
               regexp_replace(
                   ci.fantacalcio_height,
                   '[^0-9]',
                   '',
                   'g'
               ),
               ''
           ) IS NOT NULL
           AND pip.height_cm IS DISTINCT FROM
               NULLIF(
                   regexp_replace(
                       ci.fantacalcio_height,
                       '[^0-9]',
                       '',
                       'g'
                   ),
                   ''
               )::smallint
       )
    OR (
           pip.preferred_foot IS NOT NULL
           AND CASE ci.fantacalcio_foot
                   WHEN 'Destro' THEN 'RIGHT'
                   WHEN 'Sinistro' THEN 'LEFT'
                   WHEN 'Entrambi' THEN 'BOTH'
               END IS NOT NULL
           AND pip.preferred_foot IS DISTINCT FROM
               CASE ci.fantacalcio_foot
                   WHEN 'Destro' THEN 'RIGHT'
                   WHEN 'Sinistro' THEN 'LEFT'
                   WHEN 'Entrambi' THEN 'BOTH'
               END
       )
    OR (
           pip.transfermarkt_player_id IS NOT NULL
           AND NULLIF(ci.transfermarkt_player_id, '') IS NOT NULL
           AND pip.transfermarkt_player_id IS DISTINCT FROM
               NULLIF(ci.transfermarkt_player_id, '')
       );

SELECT pg_temp.assert_zero(
    COUNT(*),
    'existing identity profile unsafe verification state'
)
FROM certified_identity ci
JOIN player_source_mappings m
  ON m.source = 'FANTACALCIO'
 AND m.external_id = 'FANTACALCIO-' || ci.fantacalcio_player_id
JOIN player_identity_profiles pip
  ON pip.player_id = m.player_id
WHERE pip.identity_status NOT IN (
          'VERIFIED_COMPLETE',
          'VERIFIED_PARTIAL'
      )
   OR pip.confidence IS DISTINCT FROM 'HIGH';

\if :apply_import

CREATE TEMP TABLE certified_player_map (
    fantacalcio_player_id text PRIMARY KEY,
    player_id bigint UNIQUE NOT NULL
);

CREATE TEMP TABLE import_metrics AS
SELECT COUNT(*)::bigint AS players_before FROM players;

INSERT INTO certified_player_map
SELECT cr.fantacalcio_player_id, cr.existing_player_id::bigint
FROM certified_reconciliation cr
WHERE NULLIF(cr.existing_player_id,'') IS NOT NULL;

INSERT INTO certified_player_map
SELECT ci.fantacalcio_player_id, m.player_id
FROM certified_identity ci
JOIN player_source_mappings m ON m.source='FANTACALCIO'
 AND m.external_id='FANTACALCIO-' || ci.fantacalcio_player_id
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE new_player_assignments (
    fantacalcio_player_id text PRIMARY KEY,
    player_id bigint UNIQUE NOT NULL
);

INSERT INTO new_player_assignments (
    fantacalcio_player_id,
    player_id
)
SELECT
    ci.fantacalcio_player_id,
    nextval(pg_get_serial_sequence('players', 'id'))
FROM certified_identity ci
LEFT JOIN certified_player_map pm USING (fantacalcio_player_id)
WHERE pm.player_id IS NULL
ORDER BY ci.fantacalcio_player_id::integer;

INSERT INTO players (
    id,
    first_name,
    last_name,
    display_name,
    normalized_name,
    birth_date,
    preferred_foot,
    height_cm,
    active
)
SELECT
    npa.player_id,
    NULL,
    ci.fantacalcio_full_name,
    ci.fantacalcio_short_name,
    lower(trim(regexp_replace(translate(ci.fantacalcio_short_name,
        'ÀÁÂÃÄÅàáâãäåÈÉÊËèéêëÌÍÎÏìíîïÒÓÔÕÖòóôõöÙÚÛÜùúûüÇç',
        'AAAAAAaaaaaaEEEEeeeeIIIIiiiiOOOOOoooooUUUUuuuuCc'),
        '[^a-zA-Z0-9]+', ' ', 'g'))),
    ci.canonical_date_of_birth::date,
    CASE ci.fantacalcio_foot
        WHEN 'Destro' THEN 'RIGHT'
        WHEN 'Sinistro' THEN 'LEFT'
        WHEN 'Entrambi' THEN 'BOTH'
    END,
    NULLIF(
        regexp_replace(
            ci.fantacalcio_height,
            '[^0-9]',
            '',
            'g'
        ),
        ''
    )::smallint,
    ci.catalog_visibility = 'VISIBLE'
FROM new_player_assignments npa
JOIN certified_identity ci USING (fantacalcio_player_id);

INSERT INTO certified_player_map (
    fantacalcio_player_id,
    player_id
)
SELECT
    fantacalcio_player_id,
    player_id
FROM new_player_assignments;

SELECT pg_temp.assert_eq(
    COUNT(*),
    (
        SELECT COUNT(*)
        FROM new_player_assignments
    ),
    'new player deterministic mappings'
)
FROM certified_player_map pm
JOIN new_player_assignments npa
  ON npa.fantacalcio_player_id = pm.fantacalcio_player_id
 AND npa.player_id = pm.player_id;

SELECT pg_temp.assert_eq(COUNT(*), 497, 'resolved certified player map') FROM certified_player_map;
SELECT pg_temp.assert_eq(COUNT(*), 503-(SELECT players_before FROM import_metrics), 'new players inserted this pass')
FROM certified_player_map pm JOIN players p ON p.id=pm.player_id
WHERE p.created_at >= transaction_timestamp();

UPDATE players p SET
    birth_date=COALESCE(p.birth_date, ci.canonical_date_of_birth::date),
    preferred_foot=COALESCE(p.preferred_foot, CASE ci.fantacalcio_foot WHEN 'Destro' THEN 'RIGHT' WHEN 'Sinistro' THEN 'LEFT' WHEN 'Entrambi' THEN 'BOTH' END),
    height_cm=COALESCE(p.height_cm, NULLIF(regexp_replace(ci.fantacalcio_height,'[^0-9]','','g'),'')::smallint),
    active=(ci.catalog_visibility='VISIBLE'), updated_at=CURRENT_TIMESTAMP
FROM certified_player_map pm JOIN certified_identity ci USING (fantacalcio_player_id)
WHERE p.id=pm.player_id;

UPDATE players SET active=FALSE, updated_at=CURRENT_TIMESTAMP
WHERE id IN (56,83,180,201,508,533);

INSERT INTO player_source_mappings(player_id,source,external_id,external_name,external_url,verified)
SELECT pm.player_id,'FANTACALCIO','FANTACALCIO-'||ci.fantacalcio_player_id,
       ci.fantacalcio_short_name,ci.fantacalcio_url,TRUE
FROM certified_identity ci JOIN certified_player_map pm USING(fantacalcio_player_id)
ON CONFLICT (source,external_id) WHERE external_id IS NOT NULL DO UPDATE SET
    external_name=EXCLUDED.external_name,external_url=EXCLUDED.external_url,
    verified=TRUE,updated_at=CURRENT_TIMESTAMP;

INSERT INTO player_source_mappings(player_id,source,external_id,external_name,external_url,verified)
SELECT pm.player_id,'FANTAMARKT','TRANSFERMARKT-'||ci.transfermarkt_player_id,
       ci.transfermarkt_full_name,ci.transfermarkt_url,TRUE
FROM certified_identity ci JOIN certified_player_map pm USING(fantacalcio_player_id)
WHERE NULLIF(ci.transfermarkt_player_id,'') IS NOT NULL
ON CONFLICT (source,external_id) WHERE external_id IS NOT NULL DO UPDATE SET
    external_name=EXCLUDED.external_name,external_url=EXCLUDED.external_url,
    verified=TRUE,updated_at=CURRENT_TIMESTAMP;

INSERT INTO player_roles(player_id,season_id,source,fantasy_role,primary_role)
SELECT pm.player_id,s.id,'FANTACALCIO',ci.fantasy_role,TRUE
FROM certified_identity ci JOIN certified_player_map pm USING(fantacalcio_player_id)
CROSS JOIN seasons s WHERE s.code='2026/2027'
ON CONFLICT(player_id,season_id,source) DO UPDATE SET
 fantasy_role=EXCLUDED.fantasy_role,primary_role=TRUE;

INSERT INTO player_registrations(player_id,club_id,season_id,registration_status)
SELECT pm.player_id,c.id,s.id,
 CASE ci.season_status WHEN 'LOAN_OUT' THEN 'LOANED' WHEN 'FREE_AGENT' THEN 'RELEASED' ELSE 'ACTIVE' END
FROM certified_identity ci JOIN certified_player_map pm USING(fantacalcio_player_id)
JOIN clubs c ON lower(c.name)=lower(ci.fantacalcio_club)
CROSS JOIN seasons s WHERE s.code='2026/2027'
ON CONFLICT(player_id,season_id) DO UPDATE SET club_id=EXCLUDED.club_id,
 registration_status=EXCLUDED.registration_status,updated_at=CURRENT_TIMESTAMP;

UPDATE player_registrations SET registration_status='INACTIVE',updated_at=CURRENT_TIMESTAMP
WHERE season_id=(SELECT id FROM seasons WHERE code='2026/2027')
  AND player_id IN (56,83,180,201,508,533);

INSERT INTO player_identity_profiles(
 player_id,canonical_full_name,birth_date,nationality,height_cm,preferred_foot,
 transfermarkt_player_id,identity_status,confidence)
SELECT pm.player_id,ci.fantacalcio_full_name,ci.canonical_date_of_birth::date,
 ci.fantacalcio_nationality,
 NULLIF(regexp_replace(ci.fantacalcio_height,'[^0-9]','','g'),'')::smallint,
 CASE ci.fantacalcio_foot WHEN 'Destro' THEN 'RIGHT' WHEN 'Sinistro' THEN 'LEFT' WHEN 'Entrambi' THEN 'BOTH' END,
 NULLIF(ci.transfermarkt_player_id,''),
 CASE WHEN NULLIF(ci.transfermarkt_player_id,'') IS NULL THEN 'VERIFIED_PARTIAL' ELSE 'VERIFIED_COMPLETE' END,
 'HIGH'
FROM certified_identity ci JOIN certified_player_map pm USING(fantacalcio_player_id)
ON CONFLICT(player_id) DO UPDATE SET
 canonical_full_name=COALESCE(player_identity_profiles.canonical_full_name,EXCLUDED.canonical_full_name),
 birth_date=COALESCE(player_identity_profiles.birth_date,EXCLUDED.birth_date),
 nationality=COALESCE(player_identity_profiles.nationality,EXCLUDED.nationality),
 height_cm=COALESCE(player_identity_profiles.height_cm,EXCLUDED.height_cm),
 preferred_foot=COALESCE(player_identity_profiles.preferred_foot,EXCLUDED.preferred_foot),
 transfermarkt_player_id=COALESCE(player_identity_profiles.transfermarkt_player_id,EXCLUDED.transfermarkt_player_id),
 identity_status =
  CASE
   WHEN player_identity_profiles.identity_status = 'VERIFIED_COMPLETE'
        AND player_identity_profiles.confidence = 'HIGH'
    THEN 'VERIFIED_COMPLETE'

   WHEN player_identity_profiles.identity_status = 'VERIFIED_PARTIAL'
        AND player_identity_profiles.confidence = 'HIGH'
        AND EXCLUDED.identity_status = 'VERIFIED_COMPLETE'
        AND EXCLUDED.confidence = 'HIGH'
    THEN 'VERIFIED_COMPLETE'

   WHEN player_identity_profiles.identity_status = 'VERIFIED_PARTIAL'
        AND player_identity_profiles.confidence = 'HIGH'
    THEN 'VERIFIED_PARTIAL'

   ELSE player_identity_profiles.identity_status
  END,
 confidence = player_identity_profiles.confidence,
 updated_at=CURRENT_TIMESTAMP;

CREATE TEMP TABLE identity_evidence(field_name text, source text, external_id text,
 source_url text, player_id bigint, evidence jsonb);

INSERT INTO identity_evidence
SELECT v.field_name,'FANTACALCIO','FANTACALCIO-'||ci.fantacalcio_player_id,
 ci.fantacalcio_url,pm.player_id,jsonb_build_object('value',v.field_value,'season','2026/2027')
FROM certified_identity ci JOIN certified_player_map pm USING(fantacalcio_player_id)
CROSS JOIN LATERAL (VALUES
 ('CANONICAL_FULL_NAME',ci.fantacalcio_full_name),('BIRTH_DATE',ci.fantacalcio_dob),
 ('NATIONALITY',ci.fantacalcio_nationality),('HEIGHT_CM',ci.fantacalcio_height),
 ('PREFERRED_FOOT',ci.fantacalcio_foot)
) v(field_name,field_value);

INSERT INTO identity_evidence
SELECT v.field_name,'TRANSFERMARKT',ci.transfermarkt_player_id,ci.transfermarkt_url,
 pm.player_id,jsonb_build_object('value',v.field_value,'season','2026/2027')
FROM certified_identity ci JOIN certified_player_map pm USING(fantacalcio_player_id)
CROSS JOIN LATERAL (VALUES
 ('CANONICAL_FULL_NAME',ci.transfermarkt_full_name),('BIRTH_DATE',ci.transfermarkt_dob),
 ('NATIONALITY',ci.transfermarkt_nationality),('HEIGHT_CM',ci.transfermarkt_height),
 ('TRANSFERMARKT_PLAYER_ID',ci.transfermarkt_player_id)
) v(field_name,field_value)
WHERE NULLIF(ci.transfermarkt_player_id,'') IS NOT NULL AND NULLIF(v.field_value,'') IS NOT NULL;

INSERT INTO identity_evidence
SELECT 'BIRTH_DATE',
 CASE ci.dob_resolution_source WHEN 'UEFA' THEN 'UEFA' WHEN 'LEGA_SERIE_A' THEN 'LEGA_SERIE_A' ELSE 'OFFICIAL_CLUB' END,
 NULL,ci.dob_resolution_url,pm.player_id,
 jsonb_build_object('value',ci.canonical_date_of_birth,'resolution',ci.dob_resolution_source)
FROM certified_identity ci JOIN certified_player_map pm USING(fantacalcio_player_id)
WHERE ci.dob_resolution_status='RESOLVED_THIRD_SOURCE';

INSERT INTO player_identity_provenance(player_id,field_name,source,source_external_id,source_url,confidence,evidence)
SELECT player_id,field_name,source,NULLIF(external_id,''),source_url,'HIGH',evidence
FROM identity_evidence
ON CONFLICT(player_id,field_name,source,source_external_id) DO UPDATE SET
 source_url=EXCLUDED.source_url,confidence='HIGH',evidence=EXCLUDED.evidence,imported_at=CURRENT_TIMESTAMP;

DELETE FROM player_reconciliation_results
WHERE season_id=(SELECT id FROM seasons WHERE code='2026/2027')
 AND player_id NOT IN (
   SELECT pm.player_id FROM certified_reconciliation cr
   JOIN certified_player_map pm USING(fantacalcio_player_id)
 );

INSERT INTO player_reconciliation_results(
 season_id,player_id,normalized_name,normalized_club_name,sources_count,
 identity_resolution_method,club_resolution_source,club_verified_at)
SELECT s.id,pm.player_id,p.normalized_name,c.slug,
 (SELECT COUNT(DISTINCT m.source) FROM player_source_mappings m
   WHERE m.player_id=pm.player_id AND m.verified
     AND m.source IN ('FANTACALCIO','GAZZETTA','FANTAMARKT')),
 'EXTERNAL_ID','FANTACALCIO',CURRENT_TIMESTAMP
FROM certified_reconciliation cr JOIN certified_player_map pm USING(fantacalcio_player_id)
JOIN players p ON p.id=pm.player_id JOIN clubs c ON lower(c.name)=lower(cr.fantacalcio_club)
CROSS JOIN seasons s WHERE s.code='2026/2027'
ON CONFLICT(player_id,season_id) DO UPDATE SET
 normalized_name=EXCLUDED.normalized_name,normalized_club_name=EXCLUDED.normalized_club_name,
 sources_count=EXCLUDED.sources_count,identity_resolution_method=EXCLUDED.identity_resolution_method,
 club_resolution_source='FANTACALCIO',club_verified_at=CURRENT_TIMESTAMP,matched_at=CURRENT_TIMESTAMP;

SELECT pg_temp.assert_eq(COUNT(*),503,'players total') FROM players;
SELECT pg_temp.assert_eq(COUNT(*),495,'ACTIVE registrations') FROM player_registrations pr JOIN seasons s ON s.id=pr.season_id WHERE s.code='2026/2027' AND pr.registration_status='ACTIVE';
SELECT pg_temp.assert_eq(COUNT(*),6,'INACTIVE registrations') FROM player_registrations pr JOIN seasons s ON s.id=pr.season_id WHERE s.code='2026/2027' AND pr.registration_status='INACTIVE';
SELECT pg_temp.assert_eq(COUNT(*),1,'LOANED registrations') FROM player_registrations pr JOIN seasons s ON s.id=pr.season_id WHERE s.code='2026/2027' AND pr.registration_status='LOANED';
SELECT pg_temp.assert_eq(COUNT(*),1,'RELEASED registrations') FROM player_registrations pr JOIN seasons s ON s.id=pr.season_id WHERE s.code='2026/2027' AND pr.registration_status='RELEASED';
SELECT pg_temp.assert_eq(COUNT(*),497,'identity profiles') FROM player_identity_profiles;
SELECT pg_temp.assert_eq(COUNT(*),495,'visible reconciliation') FROM player_reconciliation_results r JOIN seasons s ON s.id=r.season_id WHERE s.code='2026/2027';
SELECT pg_temp.assert_zero(COUNT(*),'duplicate source mappings') FROM (SELECT source,external_id FROM player_source_mappings WHERE external_id IS NOT NULL GROUP BY 1,2 HAVING COUNT(*)>1) d;
SELECT pg_temp.assert_zero(COUNT(*),'duplicate identity provenance') FROM (SELECT player_id,field_name,source,source_external_id FROM player_identity_provenance GROUP BY 1,2,3,4 HAVING COUNT(*)>1) d;
SELECT pg_temp.assert_eq(COUNT(*),1,'Maldini final id258 Cagliari') FROM player_registrations pr JOIN clubs c ON c.id=pr.club_id JOIN seasons s ON s.id=pr.season_id WHERE pr.player_id=258 AND s.code='2026/2027' AND c.name='Cagliari' AND pr.registration_status='ACTIVE';
SELECT pg_temp.assert_eq(COUNT(*),1,'Russo final id510 LOANED') FROM player_registrations pr JOIN seasons s ON s.id=pr.season_id WHERE pr.player_id=510 AND s.code='2026/2027' AND pr.registration_status='LOANED';
SELECT pg_temp.assert_eq(COUNT(*),1,'Vogliacco final id533 INACTIVE') FROM player_registrations pr JOIN seasons s ON s.id=pr.season_id WHERE pr.player_id=533 AND s.code='2026/2027' AND pr.registration_status='INACTIVE';

COMMIT;
\else
ROLLBACK;
\endif
