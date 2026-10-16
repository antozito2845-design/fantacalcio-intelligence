-- ============================================================
-- V45
-- Preserve UEFA as an explicit authoritative identity source.
--
-- V44 introduced FANTACALCIO provenance support.
-- Some certified identity conflicts were independently resolved
-- using UEFA, therefore UEFA must remain distinguishable from
-- generic MANUAL_VERIFIED provenance.
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
        'UEFA',
        'FIGC',
        'LEGA_SERIE_A',
        'OFFICIAL_CLUB',
        'MANUAL_VERIFIED'
    )
);
