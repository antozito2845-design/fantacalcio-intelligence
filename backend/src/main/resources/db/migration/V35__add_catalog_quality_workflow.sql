-- ============================================================
-- V35 - CATALOG QUALITY WORKFLOW
--
-- Adds:
-- 1. persistent unresolved reconciliation review queue
-- 2. transfer detection deduplication/provenance
-- ============================================================

CREATE TABLE reconciliation_review_items (
    id BIGSERIAL PRIMARY KEY,

    season_id BIGINT NOT NULL,

    fantacalcio_batch_id BIGINT NOT NULL,
    gazzetta_batch_id BIGINT NOT NULL,
    fantamarkt_batch_id BIGINT,

    fantacalcio_external_id VARCHAR(150) NOT NULL,
    fantacalcio_name VARCHAR(150) NOT NULL,
    fantacalcio_club VARCHAR(100),

    gazzetta_external_id VARCHAR(150),
    gazzetta_name VARCHAR(150),
    gazzetta_club VARCHAR(100),

    fantamarkt_external_id VARCHAR(150),
    fantamarkt_name VARCHAR(150),
    fantamarkt_club VARCHAR(100),

    review_status VARCHAR(20)
        NOT NULL DEFAULT 'OPEN',

    resolution_source VARCHAR(30),
    resolution_external_id VARCHAR(150),
    resolution_note TEXT,

    first_seen_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    last_seen_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    resolved_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reconciliation_review_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_reconciliation_review_fc_batch
        FOREIGN KEY (fantacalcio_batch_id)
        REFERENCES import_batches(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_reconciliation_review_ga_batch
        FOREIGN KEY (gazzetta_batch_id)
        REFERENCES import_batches(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_reconciliation_review_fm_batch
        FOREIGN KEY (fantamarkt_batch_id)
        REFERENCES import_batches(id)
        ON DELETE SET NULL,

    CONSTRAINT ck_reconciliation_review_status
        CHECK (
            review_status IN (
                'OPEN',
                'RESOLVED',
                'IGNORED'
            )
        ),

    CONSTRAINT ck_reconciliation_review_resolution_source
        CHECK (
            resolution_source IS NULL
            OR resolution_source IN (
                'GAZZETTA',
                'FANTAMARKT'
            )
        ),

    CONSTRAINT uq_reconciliation_review_player
        UNIQUE (
            season_id,
            fantacalcio_external_id
        )
);

CREATE INDEX idx_reconciliation_review_status
    ON reconciliation_review_items(
        season_id,
        review_status,
        last_seen_at DESC
    );

CREATE INDEX idx_reconciliation_review_fc_external
    ON reconciliation_review_items(
        fantacalcio_external_id
    );

ALTER TABLE player_transfer_claims
ADD COLUMN detection_key VARCHAR(255);

CREATE UNIQUE INDEX uq_transfer_claim_detection_key
    ON player_transfer_claims(detection_key)
    WHERE detection_key IS NOT NULL;

CREATE INDEX idx_transfer_claim_open_player
    ON player_transfer_claims(
        player_id,
        season_id,
        verification_status
    )
    WHERE verification_status IN (
        'DETECTED',
        'PENDING_VERIFICATION'
    );