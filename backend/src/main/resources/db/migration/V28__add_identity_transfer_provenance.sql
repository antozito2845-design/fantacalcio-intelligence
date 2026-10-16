-- ============================================================
-- RECONCILIATION V2
-- Identity aliases, transfer verification and data provenance
-- ============================================================

CREATE TABLE player_identity_aliases (
    id BIGSERIAL PRIMARY KEY,

    player_id BIGINT NOT NULL,

    source VARCHAR(30) NOT NULL,
    external_id VARCHAR(150),
    alias_name VARCHAR(150) NOT NULL,
    normalized_alias VARCHAR(150) NOT NULL,

    verified BOOLEAN NOT NULL DEFAULT FALSE,
    verification_method VARCHAR(30),

    observed_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    verified_at TIMESTAMP WITH TIME ZONE,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_identity_alias_player
        FOREIGN KEY (player_id)
        REFERENCES players(id)
        ON DELETE CASCADE,

    CONSTRAINT ck_identity_alias_source
        CHECK (
            source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT',
                'FIGC',
                'LEGA_SERIE_A',
                'OFFICIAL_CLUB'
            )
        ),

    CONSTRAINT ck_identity_alias_verification_method
        CHECK (
            verification_method IS NULL
            OR verification_method IN (
                'EXTERNAL_ID',
                'EXACT_NAME',
                'HISTORICAL_MAPPING',
                'OFFICIAL_SOURCE',
                'MANUAL_VERIFICATION'
            )
        )
);

CREATE INDEX idx_identity_alias_player
    ON player_identity_aliases(player_id);

CREATE INDEX idx_identity_alias_normalized
    ON player_identity_aliases(
        normalized_alias,
        source
    );

CREATE UNIQUE INDEX uq_identity_alias_source_external
    ON player_identity_aliases(
        source,
        external_id
    )
    WHERE external_id IS NOT NULL;


-- ------------------------------------------------------------
-- Transfer / club-change claims
-- ------------------------------------------------------------

CREATE TABLE player_transfer_claims (
    id BIGSERIAL PRIMARY KEY,

    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,

    from_club_id BIGINT NOT NULL,
    to_club_id BIGINT,

    destination_club_name VARCHAR(150),

    source VARCHAR(30) NOT NULL,
    source_reference TEXT NOT NULL,

    source_published_at TIMESTAMP WITH TIME ZONE,
    observed_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    effective_at DATE,

    verification_status VARCHAR(30)
        NOT NULL DEFAULT 'DETECTED',

    verified_at TIMESTAMP WITH TIME ZONE,

    rejection_reason TEXT,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transfer_claim_player
        FOREIGN KEY (player_id)
        REFERENCES players(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_transfer_claim_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_transfer_claim_from_club
        FOREIGN KEY (from_club_id)
        REFERENCES clubs(id),

    CONSTRAINT fk_transfer_claim_to_club
        FOREIGN KEY (to_club_id)
        REFERENCES clubs(id),

    CONSTRAINT ck_transfer_claim_source
        CHECK (
            source IN (
                'FIGC',
                'LEGA_SERIE_A',
                'OFFICIAL_CLUB',
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT'
            )
        ),

    CONSTRAINT ck_transfer_verification_status
        CHECK (
            verification_status IN (
                'DETECTED',
                'PENDING_VERIFICATION',
                'OFFICIAL',
                'REJECTED'
            )
        ),

    CONSTRAINT ck_transfer_destination
        CHECK (
            to_club_id IS NOT NULL
            OR destination_club_name IS NOT NULL
        )
);

CREATE INDEX idx_transfer_claim_player_season
    ON player_transfer_claims(
        player_id,
        season_id
    );

CREATE INDEX idx_transfer_claim_pending
    ON player_transfer_claims(
        season_id,
        verification_status,
        observed_at DESC
    );


-- ------------------------------------------------------------
-- Immutable history of official registration changes
-- ------------------------------------------------------------

CREATE TABLE player_registration_events (
    id BIGSERIAL PRIMARY KEY,

    player_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,

    from_club_id BIGINT NOT NULL,
    to_club_id BIGINT,

    destination_club_name VARCHAR(150),

    event_type VARCHAR(30) NOT NULL,

    effective_at DATE NOT NULL,

    transfer_claim_id BIGINT NOT NULL,

    source VARCHAR(30) NOT NULL,
    source_reference TEXT NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_registration_event_player
        FOREIGN KEY (player_id)
        REFERENCES players(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_registration_event_season
        FOREIGN KEY (season_id)
        REFERENCES seasons(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_registration_event_from_club
        FOREIGN KEY (from_club_id)
        REFERENCES clubs(id),

    CONSTRAINT fk_registration_event_to_club
        FOREIGN KEY (to_club_id)
        REFERENCES clubs(id),

    CONSTRAINT fk_registration_event_claim
        FOREIGN KEY (transfer_claim_id)
        REFERENCES player_transfer_claims(id),

    CONSTRAINT uq_registration_event_claim
        UNIQUE (transfer_claim_id),

    CONSTRAINT ck_registration_event_type
        CHECK (
            event_type IN (
                'TRANSFER_IN_SERIE_A',
                'LEFT_SERIE_A'
            )
        )
);

CREATE INDEX idx_registration_events_player
    ON player_registration_events(
        player_id,
        season_id,
        effective_at DESC
    );


-- ------------------------------------------------------------
-- Provenance of reconciliation decisions
-- ------------------------------------------------------------

ALTER TABLE player_reconciliation_results
ADD COLUMN identity_resolution_method VARCHAR(30)
    NOT NULL DEFAULT 'LEGACY_NAME_CLUB';

ALTER TABLE player_reconciliation_results
ADD COLUMN club_resolution_source VARCHAR(30)
    NOT NULL DEFAULT 'FANTACALCIO';

ALTER TABLE player_reconciliation_results
ADD COLUMN club_verified_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE player_reconciliation_results
ADD CONSTRAINT ck_reconciliation_identity_method
CHECK (
    identity_resolution_method IN (
        'LEGACY_NAME_CLUB',
        'EXTERNAL_ID',
        'HISTORICAL_MAPPING',
        'EXACT_NAME_SAME_CLUB',
        'EXACT_NAME_UNIQUE',
        'VERIFIED_ALIAS'
    )
);

ALTER TABLE player_reconciliation_results
ADD CONSTRAINT ck_reconciliation_club_source
CHECK (
    club_resolution_source IN (
        'FANTACALCIO',
        'FIGC',
        'LEGA_SERIE_A',
        'OFFICIAL_CLUB'
    )
);


-- ------------------------------------------------------------
-- Temporal provenance for imported source snapshots
-- ------------------------------------------------------------

ALTER TABLE import_batches
ADD COLUMN source_reference TEXT;

ALTER TABLE import_batches
ADD COLUMN source_observed_at TIMESTAMP WITH TIME ZONE;