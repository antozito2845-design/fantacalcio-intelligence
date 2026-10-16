-- ============================================================
-- V37 - FIX LEGACY RECONCILIATION IDENTITY CONSTRAINT
--
-- V28 created ck_reconciliation_identity_method.
-- Later migrations evolved the supported identity methods
-- using chk_reconciliation_identity_resolution_method but did
-- not remove the original V28 constraint.
--
-- The obsolete constraint blocks valid reconciliation methods
-- such as NORMALIZED_WITHOUT_INITIALS.
-- ============================================================

ALTER TABLE player_reconciliation_results
DROP CONSTRAINT IF EXISTS
    ck_reconciliation_identity_method;

-- Keep one canonical constraint containing every currently
-- supported identity-resolution method.

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
        'NORMALIZED_WITHOUT_INITIALS'
    )
);