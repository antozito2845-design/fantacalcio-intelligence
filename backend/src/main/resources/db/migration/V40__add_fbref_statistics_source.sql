ALTER TABLE player_season_statistics
    DROP CONSTRAINT ck_statistics_source;

ALTER TABLE player_season_statistics
    ADD CONSTRAINT ck_statistics_source
        CHECK (
            source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT',
                'FBREF',
                'INTERNAL'
            )
        );

ALTER TABLE player_statistics_import_batches
    DROP CONSTRAINT ck_statistics_import_batch_source;

ALTER TABLE player_statistics_import_batches
    ADD CONSTRAINT ck_statistics_import_batch_source
        CHECK (
            source IN (
                'FANTACALCIO',
                'GAZZETTA',
                'FANTAMARKT',
                'FBREF'
            )
        );