CREATE TABLE auction_sessions (
    id BIGSERIAL PRIMARY KEY,
    squad_id BIGINT NOT NULL REFERENCES fantasy_squads(id),
    season_id BIGINT NOT NULL REFERENCES seasons(id),
    league_teams INTEGER NOT NULL CHECK (league_teams BETWEEN 6 AND 12),
    total_budget INTEGER NOT NULL CHECK (total_budget BETWEEN 150 AND 1000),
    defense_modifier BOOLEAN NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','ACTIVE','COMPLETED','CANCELLED')),
    player_state_initialized BOOLEAN NOT NULL DEFAULT FALSE,
    current_player_id BIGINT REFERENCES players(id),
    current_price INTEGER CHECK (current_price IS NULL OR current_price >= 1),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auction_nomination_pair CHECK (
        (current_player_id IS NULL AND current_price IS NULL)
        OR (current_player_id IS NOT NULL AND current_price IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_active_auction_session
    ON auction_sessions(status) WHERE status = 'ACTIVE';
CREATE INDEX idx_auction_sessions_squad_status
    ON auction_sessions(squad_id, status);

CREATE TABLE auction_player_states (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES auction_sessions(id),
    player_id BIGINT NOT NULL REFERENCES players(id),
    fantasy_role VARCHAR(5) NOT NULL CHECK (fantasy_role IN ('P','D','C','A')),
    availability VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (availability IN ('AVAILABLE','ACQUIRED_BY_USER','ACQUIRED_BY_OTHER','EXCLUDED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_auction_player_state UNIQUE (session_id, player_id)
);

CREATE INDEX idx_auction_player_state_availability
    ON auction_player_states(session_id, availability, fantasy_role);

CREATE TABLE auction_purchases (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES auction_sessions(id),
    player_id BIGINT NOT NULL REFERENCES players(id),
    buyer_type VARCHAR(10) NOT NULL CHECK (buyer_type IN ('USER','OTHER')),
    paid_price INTEGER,
    idempotency_key VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auction_purchase_price CHECK (
        (buyer_type='USER' AND paid_price IS NOT NULL AND paid_price >= 1)
        OR (buyer_type='OTHER' AND (paid_price IS NULL OR paid_price >= 1))
    ),
    CONSTRAINT uq_auction_purchase_player UNIQUE (session_id, player_id),
    CONSTRAINT uq_auction_purchase_idempotency UNIQUE (session_id, idempotency_key)
);

CREATE INDEX idx_auction_purchases_session_created
    ON auction_purchases(session_id, created_at, id);

CREATE TABLE auction_events (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES auction_sessions(id),
    event_type VARCHAR(40) NOT NULL,
    player_id BIGINT REFERENCES players(id),
    idempotency_key VARCHAR(120),
    details JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auction_events_session_created
    ON auction_events(session_id, created_at, id);
