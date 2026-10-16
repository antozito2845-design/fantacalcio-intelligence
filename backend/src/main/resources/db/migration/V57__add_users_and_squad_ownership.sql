CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_app_user_username_nonblank CHECK (BTRIM(username) <> ''),
    CONSTRAINT ck_app_user_email_nonblank CHECK (BTRIM(email) <> ''),
    CONSTRAINT ck_app_user_password_nonblank CHECK (BTRIM(password_hash) <> ''),
    CONSTRAINT ck_app_user_role CHECK (role IN ('USER', 'ADMIN'))
);

CREATE UNIQUE INDEX uq_app_users_username_ci ON app_users (LOWER(username));
CREATE UNIQUE INDEX uq_app_users_email_ci ON app_users (LOWER(email));

ALTER TABLE fantasy_squads ADD COLUMN owner_username VARCHAR(100);

UPDATE fantasy_squads
SET owner_username = 'legacy-owner'
WHERE owner_username IS NULL;

ALTER TABLE fantasy_squads ALTER COLUMN owner_username SET NOT NULL;
ALTER TABLE fantasy_squads ADD CONSTRAINT ck_fantasy_squad_owner_nonblank
    CHECK (BTRIM(owner_username) <> '');

CREATE INDEX idx_fantasy_squads_owner_season
    ON fantasy_squads (LOWER(owner_username), league_configuration_id);

DROP INDEX IF EXISTS uq_active_fantasy_squad;

CREATE UNIQUE INDEX uq_active_fantasy_squad_per_owner
    ON fantasy_squads (LOWER(owner_username))
    WHERE active = TRUE;
