-- B66 - Complete operational historical-performance population.
--
-- B49 was built when the operational catalogue contained 495 players.
-- B66 must support the live catalogue without inventing historical data.
--
-- A current player may legitimately have zero usable historical seasons:
-- youth player, new professional, unavailable source evidence, etc.
-- Missing evidence remains NULL.

ALTER TABLE player_performance_feature_profiles
    DROP CONSTRAINT ck_player_performance_history;

ALTER TABLE player_performance_feature_profiles
    ADD CONSTRAINT ck_player_performance_history
        CHECK (history_seasons_available BETWEEN 0 AND 2);

ALTER TABLE player_performance_feature_profiles
    DROP CONSTRAINT ck_player_performance_source_mix;

ALTER TABLE player_performance_feature_profiles
    ADD CONSTRAINT ck_player_performance_source_mix
        CHECK (
            source_mix IN (
                'TWO_FANTACALCIO',
                'MIXED',
                'TWO_INTERNATIONAL',
                'ONE_SEASON_ONLY',
                'NO_HISTORY'
            )
        );

ALTER TABLE player_performance_feature_profiles
    ALTER COLUMN latest_history_season DROP NOT NULL;