ALTER TABLE player_identity_profiles
    ADD COLUMN nationality VARCHAR(180),
    ADD COLUMN transfermarkt_player_id VARCHAR(30);

ALTER TABLE player_identity_profiles
    ADD CONSTRAINT uq_identity_profile_transfermarkt_id UNIQUE (transfermarkt_player_id),
    ADD CONSTRAINT ck_identity_profile_transfermarkt_id CHECK (
        transfermarkt_player_id IS NULL OR transfermarkt_player_id ~ '^[1-9][0-9]*$'
    );

ALTER TABLE player_identity_provenance DROP CONSTRAINT ck_identity_provenance_field;
ALTER TABLE player_identity_provenance ADD CONSTRAINT ck_identity_provenance_field CHECK (field_name IN (
    'CANONICAL_FULL_NAME', 'FIRST_NAME', 'LAST_NAME', 'BIRTH_DATE',
    'NATIONALITY', 'NATIONALITY_CODE', 'PLACE_OF_BIRTH', 'HEIGHT_CM', 'PREFERRED_FOOT',
    'TRANSFERMARKT_PLAYER_ID', 'IDENTITY_STATUS'
));

ALTER TABLE player_identity_provenance DROP CONSTRAINT ck_identity_provenance_source;
ALTER TABLE player_identity_provenance ADD CONSTRAINT ck_identity_provenance_source CHECK (source IN (
    'WIKIDATA', 'TRANSFERMARKT', 'FIGC', 'LEGA_SERIE_A', 'OFFICIAL_CLUB', 'MANUAL_VERIFIED'
));
