CREATE TABLE verified_source_identity_links (
    id BIGSERIAL PRIMARY KEY,

    fantacalcio_external_id VARCHAR(150) NOT NULL,

    secondary_source VARCHAR(30) NOT NULL,
    secondary_external_id VARCHAR(150) NOT NULL,

    verification_method VARCHAR(30) NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT TRUE,

    verification_note TEXT,

    created_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITH TIME ZONE
        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_verified_identity_link_source
        CHECK (
            secondary_source IN (
                'GAZZETTA',
                'FANTAMARKT'
            )
        ),

    CONSTRAINT ck_verified_identity_link_method
        CHECK (
            verification_method IN (
                'MANUAL_VERIFICATION',
                'OFFICIAL_SOURCE',
                'EXTERNAL_ID',
                'HISTORICAL_MAPPING'
            )
        ),

    CONSTRAINT uq_verified_identity_link_fantacalcio_source
        UNIQUE (
            fantacalcio_external_id,
            secondary_source
        ),

    CONSTRAINT uq_verified_identity_link_secondary
        UNIQUE (
            secondary_source,
            secondary_external_id
        )
);

CREATE INDEX idx_verified_identity_link_fantacalcio
    ON verified_source_identity_links(
        fantacalcio_external_id
    );

CREATE INDEX idx_verified_identity_link_secondary
    ON verified_source_identity_links(
        secondary_source,
        secondary_external_id
    );

INSERT INTO verified_source_identity_links (
    fantacalcio_external_id,
    secondary_source,
    secondary_external_id,
    verification_method,
    verified,
    verification_note
)
VALUES
    (
        'FANTACALCIO-6989',
        'GAZZETTA',
        'GAZZETTA-2026-D-0066',
        'MANUAL_VERIFICATION',
        TRUE,
        'Tiago Gabriel <-> GABRIEL'
    ),
    (
        'FANTACALCIO-5620',
        'GAZZETTA',
        'GAZZETTA-2026-D-0130',
        'MANUAL_VERIFICATION',
        TRUE,
        'Tavares N. <-> NUNO TAVARES'
    ),
    (
        'FANTACALCIO-7131',
        'GAZZETTA',
        'GAZZETTA-2026-D-0125',
        'MANUAL_VERIFICATION',
        TRUE,
        'Floriani Mussolini <-> MUSSOLINI'
    ),
    (
        'FANTACALCIO-4220',
        'GAZZETTA',
        'GAZZETTA-2026-D-0199',
        'MANUAL_VERIFICATION',
        TRUE,
        'Zambo Anguissa <-> ANGUISSA'
    ),
    (
        'FANTACALCIO-6783',
        'GAZZETTA',
        'GAZZETTA-2026-C-0055',
        'MANUAL_VERIFICATION',
        TRUE,
        'Unai Gomez <-> GOMEZ'
    ),
    (
        'FANTACALCIO-4911',
        'GAZZETTA',
        'GAZZETTA-2026-C-0089',
        'MANUAL_VERIFICATION',
        TRUE,
        'Douglas Luiz <-> LUIZ'
    ),
    (
        'FANTACALCIO-6664',
        'GAZZETTA',
        'GAZZETTA-2026-D-0050',
        'MANUAL_VERIFICATION',
        TRUE,
        'Delprato <-> DEL PRATO'
    ),
    (
        'FANTACALCIO-4317',
        'GAZZETTA',
        'GAZZETTA-2026-D-0128',
        'MANUAL_VERIFICATION',
        TRUE,
        'N''Dicka <-> NDICKA'
    ),
    (
        'FANTACALCIO-6766',
        'GAZZETTA',
        'GAZZETTA-2026-D-0095',
        'MANUAL_VERIFICATION',
        TRUE,
        'Kelly L. <-> KELL Y'
    ),
    (
        'FANTACALCIO-5504',
        'GAZZETTA',
        'GAZZETTA-2026-C-0022',
        'MANUAL_VERIFICATION',
        TRUE,
        'Coulibaly L. <-> COULIBAL Y'
    );

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