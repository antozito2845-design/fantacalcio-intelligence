DO $$
BEGIN
    IF (SELECT COUNT(*) FROM seasons WHERE current_season = TRUE) > 1 THEN
        RAISE EXCEPTION 'Cannot enforce current season uniqueness: multiple current seasons exist';
    END IF;
END
$$;

CREATE UNIQUE INDEX uq_seasons_single_current
    ON seasons (current_season)
    WHERE current_season = TRUE;
