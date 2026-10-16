-- ============================================================
-- V44 - ALLOW FANTACALCIO IDENTITY PROVENANCE
--
-- Fantacalcio remains the authority for fantasy role and is
-- also a legitimate observed source for player identity fields.
--
-- This migration does NOT make Fantacalcio authoritative over
-- conflicting canonical identity data.
--
-- Conflicting values remain preserved as provenance while the
-- canonical value may be resolved by an authoritative third
-- source such as LEGA_SERIE_A, OFFICIAL_CLUB or FIGC.
-- ============================================================

ALTER TABLE player_identity_provenance
DROP CONSTRAINT ck_identity_provenance_source;

ALTER TABLE player_identity_provenance
ADD CONSTRAINT ck_identity_provenance_source
CHECK (
    source IN (
        'WIKIDATA',
        'FANTACALCIO',
        'TRANSFERMARKT',
        'FIGC',
        'LEGA_SERIE_A',
        'OFFICIAL_CLUB',
        'MANUAL_VERIFIED'
    )
);
