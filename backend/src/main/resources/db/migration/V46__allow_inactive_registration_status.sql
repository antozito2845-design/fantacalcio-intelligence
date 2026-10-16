-- ============================================================
-- V46 - ALLOW GENERIC INACTIVE SEASON REGISTRATION
--
-- INACTIVE means:
-- the player identity and historical data are preserved,
-- but the player is not part of the operational catalog for
-- the season and the exact reason of the exit is not certified.
--
-- Specific verified states remain:
-- TRANSFERRED, LOANED, RELEASED.
-- ============================================================

ALTER TABLE player_registrations
DROP CONSTRAINT ck_registration_status;

ALTER TABLE player_registrations
ADD CONSTRAINT ck_registration_status
CHECK (
    registration_status IN (
        'ACTIVE',
        'TRANSFERRED',
        'LOANED',
        'RELEASED',
        'INACTIVE'
    )
);