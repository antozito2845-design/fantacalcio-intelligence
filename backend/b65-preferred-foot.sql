BEGIN;

CREATE TEMP TABLE b65_preferred_foot (
    player_id BIGINT PRIMARY KEY,
    preferred_foot VARCHAR(20) NOT NULL,
    transfermarkt_player_id VARCHAR(50) NOT NULL,
    source_url TEXT NOT NULL
);

INSERT INTO b65_preferred_foot (
    player_id,
    preferred_foot,
    transfermarkt_player_id,
    source_url
)
VALUES
(45,'BOTH','393238','https://www.transfermarkt.com/zakaria-aboukhlal/profil/spieler/393238'),
(119,'BOTH','255838','https://www.transfermarkt.com/patrick-ciurria/profil/spieler/255838'),
(134,'BOTH','533738','https://www.transfermarkt.com/jonathan-david/profil/spieler/533738'),
(195,'RIGHT','242940','https://www.transfermarkt.com/edoardo-goldaniga/profil/spieler/242940'),
(217,'LEFT','462823','https://www.transfermarkt.com/gustav-isaksen/profil/spieler/462823'),
(248,'RIGHT','192735','https://www.transfermarkt.com/stanislav-lobotka/profil/spieler/192735'),
(282,'BOTH','55735','https://www.transfermarkt.com/henrikh-mkhitaryan/profil/spieler/55735'),
(284,'RIGHT','27992','https://www.transfermarkt.com/luka-modric/profil/spieler/27992'),
(294,'RIGHT','651005','https://www.transfermarkt.com/cher-ndour/profil/spieler/651005'),
(338,'LEFT','392538','https://www.transfermarkt.com/tommaso-pobega/profil/spieler/392538'),
(350,'RIGHT','405885','https://www.transfermarkt.com/giacomo-raspadori/profil/spieler/405885'),
(352,'RIGHT','557407','https://www.transfermarkt.com/devyne-rensch/profil/spieler/557407'),
(355,'RIGHT','257732','https://www.transfermarkt.com/amir-rrahmani/profil/spieler/257732'),
(363,'RIGHT','315867','https://www.transfermarkt.com/gianluca-scamacca/profil/spieler/315867'),
(388,'BOTH','1159864','https://www.transfermarkt.com/mariano-troilo/profil/spieler/1159864'),
(406,'BOTH','845654','https://www.transfermarkt.com/kenan-yildiz/profil/spieler/845654'),
(424,'BOTH','929994','https://www.transfermarkt.com/kerim-alajbegovic/profil/spieler/929994'),
(509,'BOTH','903706','https://www.transfermarkt.com/albion-rrahmani/profil/spieler/903706'),
(520,'RIGHT','982267','https://www.transfermarkt.com/ibrahim-sulemana/profil/spieler/982267'),
(542,'RIGHT','649016','https://www.transfermarkt.com/el-bilal-toure/profil/spieler/649016');

DO $$
DECLARE
    row_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO row_count
    FROM b65_preferred_foot;

    IF row_count <> 20 THEN
        RAISE EXCEPTION
            'Attesi 20 preferred foot, trovati %',
            row_count;
    END IF;
END
$$;

UPDATE player_identity_profiles ip
SET preferred_foot = b.preferred_foot,
    updated_at = CURRENT_TIMESTAMP
FROM b65_preferred_foot b
WHERE ip.player_id = b.player_id
  AND ip.transfermarkt_player_id = b.transfermarkt_player_id
  AND (
      ip.preferred_foot IS NULL
      OR TRIM(ip.preferred_foot) = ''
  );

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
    b.player_id,
    'PREFERRED_FOOT',
    'TRANSFERMARKT',
    b.transfermarkt_player_id,
    b.source_url,
    'HIGH',
    jsonb_build_object(
        'reason',
        'Preferred foot verificato sul profilo Transfermarkt durante B65'
    )
FROM b65_preferred_foot b
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

DO $$
DECLARE
    remaining INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO remaining
    FROM b65_preferred_foot b
    JOIN player_identity_profiles ip
      ON ip.player_id = b.player_id
    WHERE ip.preferred_foot IS NULL
       OR TRIM(ip.preferred_foot) = '';

    IF remaining <> 0 THEN
        RAISE EXCEPTION
            'Restano % preferred foot mancanti',
            remaining;
    END IF;
END
$$;

COMMIT;