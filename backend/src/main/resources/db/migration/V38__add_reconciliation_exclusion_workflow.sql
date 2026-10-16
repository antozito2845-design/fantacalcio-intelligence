-- ============================================================
-- V38 - RECONCILIATION EXCLUSION WORKFLOW
--
-- Adds a first-class exclusion state for Fantacalcio list
-- entries that must not enter the operational Serie A catalog.
--
-- EXCLUDED is NOT an identity resolution.
-- It does NOT create a verified_source_identity_link.
-- ============================================================

ALTER TABLE reconciliation_review_items
DROP CONSTRAINT IF EXISTS ck_reconciliation_review_status;

ALTER TABLE reconciliation_review_items
ADD CONSTRAINT ck_reconciliation_review_status
CHECK (
    review_status IN (
        'OPEN',
        'RESOLVED',
        'IGNORED',
        'EXCLUDED'
    )
);

ALTER TABLE reconciliation_review_items
ADD COLUMN exclusion_reason VARCHAR(40);

ALTER TABLE reconciliation_review_items
ADD COLUMN exclusion_note TEXT;

ALTER TABLE reconciliation_review_items
ADD COLUMN excluded_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE reconciliation_review_items
ADD CONSTRAINT ck_reconciliation_review_exclusion_reason
CHECK (
    exclusion_reason IS NULL
    OR exclusion_reason IN (
        'OUT_OF_SERIE_A'
    )
);

ALTER TABLE reconciliation_review_items
ADD CONSTRAINT ck_reconciliation_review_exclusion_consistency
CHECK (
    (
        review_status = 'EXCLUDED'
        AND exclusion_reason IS NOT NULL
        AND excluded_at IS NOT NULL
    )
    OR
    (
        review_status <> 'EXCLUDED'
        AND exclusion_reason IS NULL
        AND excluded_at IS NULL
    )
);
