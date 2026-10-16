ALTER TABLE player_transfer_claims
    ADD COLUMN verification_source VARCHAR(30),
    ADD COLUMN verification_reference TEXT,
    ADD COLUMN verification_published_at TIMESTAMP WITH TIME ZONE;

UPDATE player_transfer_claims
SET verification_source = source,
    verification_reference = source_reference,
    verification_published_at = source_published_at
WHERE verification_status = 'OFFICIAL'
  AND source IN ('FIGC', 'LEGA_SERIE_A', 'OFFICIAL_CLUB');

ALTER TABLE player_transfer_claims
    ADD CONSTRAINT ck_transfer_verification_source
        CHECK (
            verification_source IS NULL
            OR verification_source IN (
                'FIGC',
                'LEGA_SERIE_A',
                'OFFICIAL_CLUB'
            )
        ),
    ADD CONSTRAINT ck_transfer_verification_evidence
        CHECK (
            (
                verification_source IS NULL
                AND verification_reference IS NULL
                AND verification_published_at IS NULL
            )
            OR (
                verification_source IS NOT NULL
                AND verification_reference IS NOT NULL
                AND BTRIM(verification_reference) <> ''
            )
        );

CREATE INDEX idx_transfer_claim_verification_evidence
    ON player_transfer_claims (
        verification_source,
        verification_published_at DESC
    )
    WHERE verification_source IS NOT NULL;
