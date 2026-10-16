ALTER TABLE player_reconciliation_results
DROP CONSTRAINT IF EXISTS chk_reconciliation_identity_resolution_method;

ALTER TABLE player_reconciliation_results
ADD CONSTRAINT chk_reconciliation_identity_resolution_method
CHECK (
    identity_resolution_method IN (
        'LEGACY_NAME_CLUB',
        'EXTERNAL_ID',
        'HISTORICAL_MAPPING',
        'EXACT_NAME_SAME_CLUB',
        'EXACT_NAME_UNIQUE',
        'VERIFIED_ALIAS',
        'SAFE_UNIQUE_ABBREVIATION',
        'NORMALIZED_WITHOUT_INITIALS',
        'UNIQUE_NORMALIZED_FINAL_TOKEN'
    )
);