-- ============================================================
-- V34 - REMOVE LEGACY SYNTHETIC PRODUCTION DATA
--
-- Removes historical development fixtures from non-test DBs:
-- - legacy synthetic players identified by their exact normalized names
-- - their player-related data
-- - three obsolete synthetic import batches
--
-- The cleanup is intentionally database-name agnostic and idempotent:
-- every deletion is restricted to exact historical fingerprints.
-- ============================================================

DO $$
BEGIN
    CREATE TEMP TABLE synthetic_player_ids (
        player_id BIGINT PRIMARY KEY
    ) ON COMMIT DROP;

    INSERT INTO synthetic_player_ids (player_id)
    SELECT id
    FROM players
    WHERE normalized_name IN (
        'giocatore test uno',
        'giocatore test due',
        'andrea valenti',
        'marco rinaldi',
        'luca sereni',
        'davide moretti',
        'matteo ferri',
        'alessandro conti',
        'simone leoni',
        'riccardo marini',
        'federico sala',
        'nicola gatti',
        'tommaso villa',
        'emanuele greco',
        'gabriele romano',
        'pietro de luca',
        'samuele caruso',
        'christian lombardi',
        'filippo santoro',
        'leonardo martini',
        'michele esposito',
        'antonio vitale',
        'daniele pellegrini',
        'kevin bianchi',
        'jacopo colombo'
    );

    DELETE FROM auction_price_snapshots
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM fantasy_squad_players
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_availabilities
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_availability_history
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_evaluations
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_market_values
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_reconciliation_results
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_registrations
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_roles
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_season_statistics
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_source_mappings
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM player_tactical_profiles
    WHERE player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    UPDATE player_statistics_import_rows
    SET resolved_player_id = NULL
    WHERE resolved_player_id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM players
    WHERE id IN (
        SELECT player_id FROM synthetic_player_ids
    );

    DELETE FROM import_batches
    WHERE
        (
            source = 'FANTACALCIO'
            AND original_filename = 'players-test.csv'
            AND total_rows = 3
            AND valid_rows = 3
            AND invalid_rows = 0
        )
        OR
        (
            source = 'GAZZETTA'
            AND original_filename = 'players-gazzetta-test.csv'
            AND total_rows = 3
            AND valid_rows = 3
            AND invalid_rows = 0
        )
        OR
        (
            source = 'FANTAMARKT'
            AND original_filename = 'players-fantamarkt-test.csv'
            AND total_rows = 3
            AND valid_rows = 3
            AND invalid_rows = 0
        );

END
$$;
