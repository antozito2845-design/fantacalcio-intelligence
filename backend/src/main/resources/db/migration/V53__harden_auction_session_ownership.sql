ALTER TABLE auction_sessions
    ADD COLUMN owner_username VARCHAR(120);

UPDATE auction_sessions
SET owner_username = 'legacy'
WHERE owner_username IS NULL;

ALTER TABLE auction_sessions
    ALTER COLUMN owner_username SET NOT NULL;

DROP INDEX IF EXISTS uq_active_auction_session;

CREATE UNIQUE INDEX uq_active_auction_session_owner
    ON auction_sessions(owner_username)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uq_active_auction_session_squad
    ON auction_sessions(squad_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_auction_sessions_owner_status
    ON auction_sessions(owner_username, status);
