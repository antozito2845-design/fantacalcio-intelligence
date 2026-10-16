-- =====================================================================
-- V69 - Selective automated identity enrichment
--
-- Keeps V68 production automation history immutable.
--
-- Purpose:
-- - durable per-player Transfermarkt automation attempts
-- - avoid retrying the same unresolved players every scheduler run
-- - prevent bounded batches from starving later eligible players
-- - preserve history counters and last result
--
-- This table does NOT assert identity truth.
-- MATCHED_VERIFIED truth is persisted only through the existing
-- Transfermarkt provenance persistence service.
-- =====================================================================

CREATE TABLE identity_enrichment_automation_attempts (
    id BIGSERIAL PRIMARY KEY,

    season_id BIGINT NOT NULL
        REFERENCES seasons(id)
        ON DELETE CASCADE,

    player_id BIGINT NOT NULL
        REFERENCES players(id)
        ON DELETE CASCADE,

    last_status VARCHAR(30) NOT NULL,

    attempt_count INTEGER NOT NULL
        DEFAULT 1,

    last_score INTEGER NOT NULL
        DEFAULT 0,

    transfermarkt_player_id VARCHAR(30),

    last_reason VARCHAR(500),

    last_attempt_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    next_retry_at TIMESTAMP WITH TIME ZONE
        NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_identity_automation_attempt_player
        UNIQUE (season_id, player_id),

    CONSTRAINT ck_identity_automation_attempt_status
        CHECK (
            last_status IN (
                'MATCHED_VERIFIED',
                'REVIEW_REQUIRED',
                'NOT_FOUND',
                'FAILED'
            )
        ),

    CONSTRAINT ck_identity_automation_attempt_count
        CHECK (attempt_count >= 1),

    CONSTRAINT ck_identity_automation_attempt_score
        CHECK (last_score BETWEEN 0 AND 100)
);

CREATE INDEX idx_identity_automation_retry
    ON identity_enrichment_automation_attempts (
        season_id,
        next_retry_at,
        player_id
    );

CREATE INDEX idx_identity_automation_status
    ON identity_enrichment_automation_attempts (
        season_id,
        last_status,
        last_attempt_at DESC
    );