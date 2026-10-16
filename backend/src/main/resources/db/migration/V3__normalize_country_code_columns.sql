ALTER TABLE clubs
    ALTER COLUMN country_code TYPE VARCHAR(2)
    USING TRIM(country_code);

ALTER TABLE players
    ALTER COLUMN nationality_code TYPE VARCHAR(2)
    USING TRIM(nationality_code);