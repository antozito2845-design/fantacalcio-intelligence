ALTER TABLE league_configurations
ADD COLUMN max_substitutions INTEGER NOT NULL DEFAULT 3;

ALTER TABLE league_configurations
ADD CONSTRAINT ck_league_max_substitutions
CHECK (max_substitutions BETWEEN 3 AND 5);