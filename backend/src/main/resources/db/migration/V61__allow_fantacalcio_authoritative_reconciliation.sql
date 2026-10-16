-- ============================================================
-- B62.2A
-- Fantacalcio authoritative operational catalog
--
-- During the transfer market a player present in the latest
-- Fantacalcio listone may be operational even when secondary
-- identity enrichment is still pending.
--
-- This migration only extends the reconciliation identity
-- method allowlist. It does not delete or rewrite historical
-- reconciliation data.
-- ============================================================

ALTER TABLE player_reconciliation_results
DROP CONSTRAINT IF EXISTS
    chk_reconciliation_identity_resolution_method;

ALTER TABLE player_reconciliation_results
ADD CONSTRAINT
    chk_reconciliation_identity_resolution_method
CHECK (
    identity_resolution_method IN (
        'LEGACY_NAME_CLUB',
        'EXTERNAL_ID',
        'HISTORICAL_MAPPING',
        'EXACT_NAME_SAME_CLUB',
        'EXACT_NAME_UNIQUE',
        'VERIFIED_ALIAS',
        'VERIFIED_SOURCE_LINK',
        'SAFE_UNIQUE_ABBREVIATION',
        'NORMALIZED_WITHOUT_INITIALS',
        'FANTACALCIO_ONLY'
    )
);