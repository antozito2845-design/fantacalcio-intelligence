CREATE TABLE player_identity_profiles (
    player_id BIGINT PRIMARY KEY REFERENCES players(id) ON DELETE CASCADE,
    canonical_full_name VARCHAR(180),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    birth_date DATE,
    nationality_code CHAR(2),
    place_of_birth VARCHAR(180),
    height_cm SMALLINT,
    preferred_foot VARCHAR(10),
    identity_status VARCHAR(40) NOT NULL,
    confidence VARCHAR(10) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_identity_profile_status CHECK (identity_status IN (
        'VERIFIED_COMPLETE', 'VERIFIED_PARTIAL', 'NO_SERIE_A_HISTORY_EXPECTED',
        'REVIEW_REQUIRED', 'DATA_SOURCE_MISSING'
    )),
    CONSTRAINT ck_identity_profile_confidence CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW', 'NONE')),
    CONSTRAINT ck_identity_profile_height CHECK (height_cm IS NULL OR height_cm BETWEEN 140 AND 220),
    CONSTRAINT ck_identity_profile_foot CHECK (
        preferred_foot IS NULL OR preferred_foot IN ('RIGHT', 'LEFT', 'BOTH')
    ),
    CONSTRAINT ck_verified_identity_minimum CHECK (
        identity_status NOT IN ('VERIFIED_COMPLETE', 'VERIFIED_PARTIAL')
        OR (confidence = 'HIGH' AND canonical_full_name IS NOT NULL AND birth_date IS NOT NULL)
    )
);

CREATE TABLE player_identity_provenance (
    id BIGSERIAL PRIMARY KEY,
    player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
    field_name VARCHAR(50) NOT NULL,
    source VARCHAR(40) NOT NULL,
    source_external_id VARCHAR(150),
    source_url TEXT,
    confidence VARCHAR(10) NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    imported_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    evidence JSONB NOT NULL DEFAULT '{}'::JSONB,

    CONSTRAINT ck_identity_provenance_field CHECK (field_name IN (
        'CANONICAL_FULL_NAME', 'FIRST_NAME', 'LAST_NAME', 'BIRTH_DATE',
        'NATIONALITY_CODE', 'PLACE_OF_BIRTH', 'HEIGHT_CM', 'PREFERRED_FOOT',
        'IDENTITY_STATUS'
    )),
    CONSTRAINT ck_identity_provenance_source CHECK (source IN (
        'WIKIDATA', 'FIGC', 'LEGA_SERIE_A', 'OFFICIAL_CLUB', 'MANUAL_VERIFIED'
    )),
    CONSTRAINT ck_identity_provenance_confidence CHECK (confidence IN ('HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT uq_identity_provenance UNIQUE NULLS NOT DISTINCT
        (player_id, field_name, source, source_external_id)
);

CREATE INDEX idx_identity_profiles_status ON player_identity_profiles(identity_status);
CREATE INDEX idx_identity_provenance_player ON player_identity_provenance(player_id, field_name);
