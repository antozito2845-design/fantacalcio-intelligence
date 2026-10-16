CREATE TABLE verified_identity_overrides (
    id BIGSERIAL PRIMARY KEY,

    season_code VARCHAR(20) NOT NULL,
    club_name VARCHAR(120) NOT NULL,
    player_display_name VARCHAR(180) NOT NULL,

    canonical_full_name VARCHAR(180) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    birth_date DATE NOT NULL,

    transfermarkt_player_id VARCHAR(30) NOT NULL,
    transfermarkt_profile_url TEXT NOT NULL,

    source VARCHAR(40) NOT NULL DEFAULT 'MANUAL_VERIFIED',
    confidence VARCHAR(10) NOT NULL DEFAULT 'HIGH',

    reason TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_verified_identity_override_player
        UNIQUE (
            season_code,
            club_name,
            player_display_name
        ),

    CONSTRAINT uq_verified_identity_override_transfermarkt
        UNIQUE (transfermarkt_player_id),

    CONSTRAINT ck_verified_identity_override_tm_id
        CHECK (
            transfermarkt_player_id ~ '^[1-9][0-9]*$'
        ),

    CONSTRAINT ck_verified_identity_override_confidence
        CHECK (
            confidence IN ('HIGH', 'MEDIUM', 'LOW')
        ),

    CONSTRAINT ck_verified_identity_override_source
        CHECK (
            source = 'MANUAL_VERIFIED'
        )
);


CREATE INDEX idx_verified_identity_override_lookup
    ON verified_identity_overrides (
        season_code,
        LOWER(club_name),
        LOWER(player_display_name)
    );


INSERT INTO verified_identity_overrides (
    season_code,
    club_name,
    player_display_name,
    canonical_full_name,
    first_name,
    last_name,
    birth_date,
    transfermarkt_player_id,
    transfermarkt_profile_url,
    source,
    confidence,
    reason
)
VALUES
(
    '2026/2027',
    'Cagliari',
    'Aurelio',
    'Giuseppe Aurelio',
    'Giuseppe',
    'Aurelio',
    DATE '2000-03-22',
    '457444',
    'https://www.transfermarkt.it/giuseppe-aurelio/profil/spieler/457444',
    'MANUAL_VERIFIED',
    'HIGH',
    'Identita verificata manualmente tramite profilo Transfermarkt'
),
(
    '2026/2027',
    'Lecce',
    'Penev',
    'Plamen Penev',
    'Plamen',
    'Penev',
    DATE '2008-04-22',
    '1084756',
    'https://www.transfermarkt.it/plamen-penev/profil/spieler/1084756',
    'MANUAL_VERIFIED',
    'HIGH',
    'Identita verificata manualmente tramite profilo Transfermarkt'
),
(
    '2026/2027',
    'Parma',
    'Romero D.',
    'David Romero',
    'David',
    'Romero',
    DATE '2003-03-25',
    '668512',
    'https://www.transfermarkt.it/david-romero/profil/spieler/668512',
    'MANUAL_VERIFIED',
    'HIGH',
    'Identita verificata manualmente tramite profilo Transfermarkt'
);