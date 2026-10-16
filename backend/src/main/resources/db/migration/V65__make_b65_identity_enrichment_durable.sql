-- B65 - Identity enrichment durability/backfill.
--
-- Runtime/future imports are normalized by the application code.
-- This migration makes already-populated databases converge to the
-- certified B65 state without overwriting unrelated populated values.

-- ============================================================
-- 1. VERIFIED NATIONALITY CORRECTIONS
-- Stable key: Transfermarkt player id.
-- ============================================================

UPDATE player_identity_profiles
SET
    nationality = 'Francia, Guinea-Bissau',
    nationality_code = 'FR',
    updated_at = CURRENT_TIMESTAMP
WHERE transfermarkt_player_id = '1131161'
  AND (
      nationality IS DISTINCT FROM 'Francia, Guinea-Bissau'
      OR nationality_code IS DISTINCT FROM 'FR'
  );

UPDATE player_identity_profiles
SET
    nationality = 'Italia',
    nationality_code = 'IT',
    updated_at = CURRENT_TIMESTAMP
WHERE transfermarkt_player_id = '992569'
  AND (
      nationality IS DISTINCT FROM 'Italia'
      OR nationality_code IS DISTINCT FROM 'IT'
  );

-- ============================================================
-- 2. NATIONALITY -> CODE BACKFILL
-- Only fills missing nationality_code values.
-- First nationality is authoritative for the canonical code.
-- ============================================================

WITH nationality_mapping(nationality_key, nationality_code) AS (
    VALUES
        ('Albania','AL'),
        ('Angola','AO'),
        ('Argentina','AR'),
        ('Armenia','AM'),
        ('Australia','AU'),
        ('Austria','AT'),
        ('Belgio','BE'),
        ('Bosnia Erzegovina','BA'),
        ('Brasile','BR'),
        ('Bulgaria','BG'),
        ('Camerun','CM'),
        ('Canada','CA'),
        ('Colombia','CO'),
        ('Costa d''Avorio','CI'),
        ('Costa D''Avorio','CI'),
        ('Croazia','HR'),
        ('Danimarca','DK'),
        ('Ecuador','EC'),
        ('England','GB'),
        ('Finlandia','FI'),
        ('Francia','FR'),
        ('Gabon','GA'),
        ('Gambia','GM'),
        ('Georgia','GE'),
        ('Germania','DE'),
        ('Ghana','GH'),
        ('Giappone','JP'),
        ('Grecia','GR'),
        ('Guinea-Bissau','GW'),
        ('Guinea Equatoriale','GQ'),
        ('Indonesia','ID'),
        ('Inghilterra','GB'),
        ('Irlanda','IE'),
        ('Islanda','IS'),
        ('Israele','IL'),
        ('Italia','IT'),
        ('Italy','IT'),
        ('Kosovo','XK'),
        ('Lituania','LT'),
        ('Mali','ML'),
        ('Marocco','MA'),
        ('Mauritania','MR'),
        ('Messico','MX'),
        ('Montenegro','ME'),
        ('Niger','NE'),
        ('Nigeria','NG'),
        ('Norvegia','NO'),
        ('Olanda','NL'),
        ('Polonia','PL'),
        ('Portogallo','PT'),
        ('Repubblica Ceca','CZ'),
        ('Romania','RO'),
        ('Scozia','GB'),
        ('Senegal','SN'),
        ('Serbia','RS'),
        ('Slovacchia','SK'),
        ('Slovenia','SI'),
        ('Spagna','ES'),
        ('Spain','ES'),
        ('Stati Uniti','US'),
        ('Suriname','SR'),
        ('Svezia','SE'),
        ('Svizzera','CH'),
        ('Togo','TG'),
        ('Turchia','TR'),
        ('Ucraina','UA'),
        ('Uruguay','UY'),
        ('Zambia','ZM')
)
UPDATE player_identity_profiles ip
SET
    nationality_code = nm.nationality_code,
    updated_at = CURRENT_TIMESTAMP
FROM nationality_mapping nm
WHERE (
        ip.nationality_code IS NULL
        OR TRIM(ip.nationality_code) = ''
      )
  AND TRIM(SPLIT_PART(ip.nationality, ',', 1)) = nm.nationality_key;

-- Keep legacy players table aligned where its canonical code is missing.

UPDATE players p
SET
    nationality_code = ip.nationality_code,
    updated_at = CURRENT_TIMESTAMP
FROM player_identity_profiles ip
WHERE ip.player_id = p.id
  AND ip.nationality_code IS NOT NULL
  AND TRIM(ip.nationality_code) <> ''
  AND (
      p.nationality_code IS NULL
      OR TRIM(p.nationality_code) = ''
  );

-- ============================================================
-- 3. VERIFIED PREFERRED FOOT BACKFILL
-- Stable key: Transfermarkt player id.
-- Never overwrites an already populated preferred foot.
-- ============================================================

WITH preferred_foot_backfill(
    transfermarkt_player_id,
    preferred_foot,
    source_url
) AS (
    VALUES
        ('393238','BOTH','https://www.transfermarkt.com/zakaria-aboukhlal/profil/spieler/393238'),
        ('255838','BOTH','https://www.transfermarkt.com/patrick-ciurria/profil/spieler/255838'),
        ('533738','BOTH','https://www.transfermarkt.com/jonathan-david/profil/spieler/533738'),
        ('242940','RIGHT','https://www.transfermarkt.com/edoardo-goldaniga/profil/spieler/242940'),
        ('462823','LEFT','https://www.transfermarkt.com/gustav-isaksen/profil/spieler/462823'),
        ('192735','RIGHT','https://www.transfermarkt.com/stanislav-lobotka/profil/spieler/192735'),
        ('55735','BOTH','https://www.transfermarkt.com/henrikh-mkhitaryan/profil/spieler/55735'),
        ('27992','RIGHT','https://www.transfermarkt.com/luka-modric/profil/spieler/27992'),
        ('651005','RIGHT','https://www.transfermarkt.com/cher-ndour/profil/spieler/651005'),
        ('392538','LEFT','https://www.transfermarkt.com/tommaso-pobega/profil/spieler/392538'),
        ('405885','RIGHT','https://www.transfermarkt.com/giacomo-raspadori/profil/spieler/405885'),
        ('557407','RIGHT','https://www.transfermarkt.com/devyne-rensch/profil/spieler/557407'),
        ('257732','RIGHT','https://www.transfermarkt.com/amir-rrahmani/profil/spieler/257732'),
        ('315867','RIGHT','https://www.transfermarkt.com/gianluca-scamacca/profil/spieler/315867'),
        ('1159864','BOTH','https://www.transfermarkt.com/mariano-troilo/profil/spieler/1159864'),
        ('845654','BOTH','https://www.transfermarkt.com/kenan-yildiz/profil/spieler/845654'),
        ('929994','BOTH','https://www.transfermarkt.com/kerim-alajbegovic/profil/spieler/929994'),
        ('903706','BOTH','https://www.transfermarkt.com/albion-rrahmani/profil/spieler/903706'),
        ('982267','RIGHT','https://www.transfermarkt.com/ibrahim-sulemana/profil/spieler/982267'),
        ('649016','RIGHT','https://www.transfermarkt.com/el-bilal-toure/profil/spieler/649016')
)
UPDATE player_identity_profiles ip
SET
    preferred_foot = pf.preferred_foot,
    updated_at = CURRENT_TIMESTAMP
FROM preferred_foot_backfill pf
WHERE ip.transfermarkt_player_id = pf.transfermarkt_player_id
  AND (
      ip.preferred_foot IS NULL
      OR TRIM(ip.preferred_foot) = ''
  );

UPDATE players p
SET
    preferred_foot = ip.preferred_foot,
    updated_at = CURRENT_TIMESTAMP
FROM player_identity_profiles ip
WHERE ip.player_id = p.id
  AND ip.preferred_foot IS NOT NULL
  AND TRIM(ip.preferred_foot) <> ''
  AND (
      p.preferred_foot IS NULL
      OR TRIM(p.preferred_foot) = ''
  );

-- ============================================================
-- 4. VERIFIED PREFERRED FOOT PROVENANCE
-- Preserve the source observation independently from canonical value.
-- ============================================================

WITH preferred_foot_backfill(
    transfermarkt_player_id,
    preferred_foot,
    source_url
) AS (
    VALUES
        ('393238','BOTH','https://www.transfermarkt.com/zakaria-aboukhlal/profil/spieler/393238'),
        ('255838','BOTH','https://www.transfermarkt.com/patrick-ciurria/profil/spieler/255838'),
        ('533738','BOTH','https://www.transfermarkt.com/jonathan-david/profil/spieler/533738'),
        ('242940','RIGHT','https://www.transfermarkt.com/edoardo-goldaniga/profil/spieler/242940'),
        ('462823','LEFT','https://www.transfermarkt.com/gustav-isaksen/profil/spieler/462823'),
        ('192735','RIGHT','https://www.transfermarkt.com/stanislav-lobotka/profil/spieler/192735'),
        ('55735','BOTH','https://www.transfermarkt.com/henrikh-mkhitaryan/profil/spieler/55735'),
        ('27992','RIGHT','https://www.transfermarkt.com/luka-modric/profil/spieler/27992'),
        ('651005','RIGHT','https://www.transfermarkt.com/cher-ndour/profil/spieler/651005'),
        ('392538','LEFT','https://www.transfermarkt.com/tommaso-pobega/profil/spieler/392538'),
        ('405885','RIGHT','https://www.transfermarkt.com/giacomo-raspadori/profil/spieler/405885'),
        ('557407','RIGHT','https://www.transfermarkt.com/devyne-rensch/profil/spieler/557407'),
        ('257732','RIGHT','https://www.transfermarkt.com/amir-rrahmani/profil/spieler/257732'),
        ('315867','RIGHT','https://www.transfermarkt.com/gianluca-scamacca/profil/spieler/315867'),
        ('1159864','BOTH','https://www.transfermarkt.com/mariano-troilo/profil/spieler/1159864'),
        ('845654','BOTH','https://www.transfermarkt.com/kenan-yildiz/profil/spieler/845654'),
        ('929994','BOTH','https://www.transfermarkt.com/kerim-alajbegovic/profil/spieler/929994'),
        ('903706','BOTH','https://www.transfermarkt.com/albion-rrahmani/profil/spieler/903706'),
        ('982267','RIGHT','https://www.transfermarkt.com/ibrahim-sulemana/profil/spieler/982267'),
        ('649016','RIGHT','https://www.transfermarkt.com/el-bilal-toure/profil/spieler/649016')
)
INSERT INTO player_identity_provenance (
    player_id,
    field_name,
    source,
    source_external_id,
    source_url,
    confidence,
    evidence
)
SELECT
    ip.player_id,
    'PREFERRED_FOOT',
    'TRANSFERMARKT',
    pf.transfermarkt_player_id,
    pf.source_url,
    'HIGH',
    jsonb_build_object(
        'value', pf.preferred_foot,
        'season', '2026/2027',
        'verification', 'B65_VERIFIED_BACKFILL'
    )
FROM preferred_foot_backfill pf
JOIN player_identity_profiles ip
  ON ip.transfermarkt_player_id = pf.transfermarkt_player_id
ON CONFLICT (
    player_id,
    field_name,
    source,
    source_external_id
)
DO UPDATE SET
    source_url = EXCLUDED.source_url,
    confidence = EXCLUDED.confidence,
    evidence = EXCLUDED.evidence,
    imported_at = CURRENT_TIMESTAMP;