package it.fantacalciointelligence.backend.reconciliation;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.PageResponse;
import it.fantacalciointelligence.backend.player.PlayerCatalogResponse;
import it.fantacalciointelligence.backend.player.PlayerCatalogService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PlayerReconciliationServiceTest {

    @Autowired
    private PlayerReconciliationService service;

    @Autowired
    private PlayerCatalogService playerCatalogService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void declareLegacy2024CompetitionMembership() {
        jdbcClient.sql("""
                INSERT INTO season_clubs(season_id,club_id)
                SELECT season.id,club.id FROM seasons season CROSS JOIN clubs club
                WHERE season.code='2024/2025' AND club.active=TRUE
                ON CONFLICT DO NOTHING
                """).update();
    }

    @Test
    void shouldBuildCatalogFromFantacalcioPlayersConfirmedByAnotherSource() {

        OffsetDateTime observedAt =
                OffsetDateTime.parse(
                        "2024-08-01T10:00:00+02:00"
                );

        long fantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        4,
                        observedAt
                );

        long gazzettaBatchId =
                createBatch(
                        "GAZZETTA",
                        3,
                        observedAt
                );

        long fantamarktBatchId =
                createBatch(
                        "FANTAMARKT",
                        2,
                        observedAt
                );

        insertRow(
                fantacalcioBatchId,
                1,
                "TEST-FC-001",
                "Riconciliazione Completo",
                "Napoli",
                "A"
        );

        insertRow(
                gazzettaBatchId,
                1,
                "TEST-GA-001",
                "Riconciliazione Completo",
                "Napoli",
                "A"
        );

        insertRow(
                fantamarktBatchId,
                1,
                "TEST-FM-001",
                "Riconciliazione Completo",
                "Napoli",
                "A"
        );

        insertRow(
                fantacalcioBatchId,
                2,
                "TEST-FC-002",
                "Riconciliazione FG",
                "Inter",
                "C"
        );

        insertRow(
                gazzettaBatchId,
                2,
                "TEST-GA-002",
                "Riconciliazione FG",
                "Inter",
                "C"
        );

        insertRow(
                fantacalcioBatchId,
                3,
                "TEST-FC-003",
                "Riconciliazione FM",
                "Roma",
                "D"
        );

        insertRow(
                fantamarktBatchId,
                2,
                "TEST-FM-003",
                "Riconciliazione FM",
                "Roma",
                "D"
        );

        insertRow(
                fantacalcioBatchId,
                4,
                "TEST-FC-004",
                "Riconciliazione Solo Fantacalcio",
                "Lecce",
                "A"
        );

        /*
         * Rows existing only in secondary sources must not create
         * catalog players.
         */
        insertRow(
                gazzettaBatchId,
                3,
                "TEST-GA-ONLY",
                "Riconciliazione Solo Gazzetta",
                "Torino",
                "C"
        );

        ReconciliationResponse response =
                service.reconcile("2024/2025");

        assertThat(response.status())
                .isEqualTo("COMPLETED");

        /*
         * Fantacalcio is authoritative for operational inclusion:
         * the fourth player is Fantacalcio-only and must still
         * enter the catalog while enrichment is pending.
         */
        assertThat(response.matchedPlayers())
                .isEqualTo(4);

        assertThat(response.matchedWithThreeSources())
                .isEqualTo(1);

        assertThat(response.matchedWithTwoSources())
                .isEqualTo(2);

        assertThat(response.excludedSingleSourcePlayers())
                .isZero();

        assertThat(
                response.createdPlayers()
                        + response.updatedPlayers()
        ).isEqualTo(4);

        PageResponse<PlayerCatalogResponse> catalog =
                playerCatalogService.findPlayers(
                        "2024/2025",
                        "Riconciliazione",
                        null,
                        null,
                        0,
                        25
                );

        assertThat(catalog.totalElements())
                .isEqualTo(4);

        assertThat(catalog.content())
                .extracting(
                        PlayerCatalogResponse::sourcesCount
                )
                .containsExactlyInAnyOrder(
                        3,
                        2,
                        2,
                        1
                );

        assertThat(catalog.content())
                .extracting(
                        PlayerCatalogResponse::name
                )
                .contains(
                        "Riconciliazione Solo Fantacalcio"
                )
                .doesNotContain(
                        "Riconciliazione Solo Gazzetta"
                );
    }

    @Test
    void shouldKeepSameIdentityWhenSecondarySourceHasDifferentClub() {

        OffsetDateTime observedAt =
                OffsetDateTime.parse(
                        "2024-08-05T10:00:00+02:00"
                );

        long fantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        1,
                        observedAt
                );

        long gazzettaBatchId =
                createBatch(
                        "GAZZETTA",
                        1,
                        observedAt
                );

        long fantamarktBatchId =
                createBatch(
                        "FANTAMARKT",
                        1,
                        observedAt
                );

        insertRow(
                fantacalcioBatchId,
                1,
                "FC-TRANSFER-IDENTITY",
                "Mercato Identity Player",
                "Roma",
                "A"
        );

        insertRow(
                gazzettaBatchId,
                1,
                "GA-TRANSFER-IDENTITY",
                "Mercato Identity Player",
                "Bologna",
                "A"
        );

        insertRow(
                fantamarktBatchId,
                1,
                "FM-TRANSFER-IDENTITY",
                "Mercato Identity Player",
                "Bologna",
                "A"
        );

        ReconciliationResponse response =
                service.reconcile("2024/2025");

        assertThat(response.matchedPlayers())
                .isEqualTo(1);

        Long playerId =
                findPlayerId(
                        "mercato identity player"
                );

        assertThat(currentClub(playerId))
                .isEqualTo("Roma");

        String resolutionMethod =
                reconciliationField(
                        playerId,
                        "identity_resolution_method"
                );

        assertThat(resolutionMethod)
                .isEqualTo("EXACT_NAME_UNIQUE");

        String clubResolutionSource =
                reconciliationField(
                        playerId,
                        "club_resolution_source"
                );

        assertThat(clubResolutionSource)
                .isEqualTo("FANTACALCIO");
    }

    @Test
    void shouldPreferNewerOfficialTransferOverOlderFantacalcioSnapshot() {

        OffsetDateTime fantacalcioObservedAt =
                OffsetDateTime.parse(
                        "2024-08-01T09:00:00+02:00"
                );

        long fantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        1,
                        fantacalcioObservedAt
                );

        long gazzettaBatchId =
                createBatch(
                        "GAZZETTA",
                        1,
                        fantacalcioObservedAt
                );

        long fantamarktBatchId =
                createBatch(
                        "FANTAMARKT",
                        1,
                        fantacalcioObservedAt
                );

        Long playerId =
                createPlayer(
                        "Official Transfer Wins"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        createMapping(
                playerId,
                "FANTACALCIO",
                "FC-OFFICIAL-WINS",
                "Official Transfer Wins"
        );

        insertRow(
                fantacalcioBatchId,
                1,
                "FC-OFFICIAL-WINS",
                "Official Transfer Wins",
                "Roma",
                "A"
        );

        insertRow(
                gazzettaBatchId,
                1,
                "GA-OFFICIAL-WINS",
                "Official Transfer Wins",
                "Bologna",
                "A"
        );

        insertRow(
                fantamarktBatchId,
                1,
                "FM-OFFICIAL-WINS",
                "Official Transfer Wins",
                "Bologna",
                "A"
        );

        createOfficialTransferClaim(
                playerId,
                "Roma",
                "Bologna",
                "OFFICIAL_CLUB",
                OffsetDateTime.parse(
                        "2024-08-05T15:00:00+02:00"
                )
        );

        ReconciliationResponse response =
                service.reconcile("2024/2025");

        assertThat(response.matchedPlayers())
                .isEqualTo(1);

        assertThat(currentClub(playerId))
                .isEqualTo("Bologna");

        assertThat(
                reconciliationField(
                        playerId,
                        "club_resolution_source"
                )
        ).isEqualTo("OFFICIAL_CLUB");
    }

    @Test
    void shouldPreferNewerFantacalcioSnapshotOverOlderOfficialTransfer() {

        OffsetDateTime fantacalcioObservedAt =
                OffsetDateTime.parse(
                        "2024-08-10T12:00:00+02:00"
                );

        long fantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        1,
                        fantacalcioObservedAt
                );

        long gazzettaBatchId =
                createBatch(
                        "GAZZETTA",
                        1,
                        fantacalcioObservedAt
                );

        long fantamarktBatchId =
                createBatch(
                        "FANTAMARKT",
                        1,
                        fantacalcioObservedAt
                );

        Long playerId =
                createPlayer(
                        "New Fantacalcio Wins"
                );

        registerPlayer(
                playerId,
                "Bologna"
        );

        createMapping(
                playerId,
                "FANTACALCIO",
                "FC-NEWER",
                "New Fantacalcio Wins"
        );

        insertRow(
                fantacalcioBatchId,
                1,
                "FC-NEWER",
                "New Fantacalcio Wins",
                "Roma",
                "C"
        );

        insertRow(
                gazzettaBatchId,
                1,
                "GA-NEWER",
                "New Fantacalcio Wins",
                "Roma",
                "C"
        );

        insertRow(
                fantamarktBatchId,
                1,
                "FM-NEWER",
                "New Fantacalcio Wins",
                "Roma",
                "C"
        );

        createOfficialTransferClaim(
                playerId,
                "Roma",
                "Bologna",
                "OFFICIAL_CLUB",
                OffsetDateTime.parse(
                        "2024-08-05T12:00:00+02:00"
                )
        );

        ReconciliationResponse response =
                service.reconcile("2024/2025");

        assertThat(response.matchedPlayers())
                .isEqualTo(1);

        assertThat(currentClub(playerId))
                .isEqualTo("Roma");

        assertThat(
                reconciliationField(
                        playerId,
                        "club_resolution_source"
                )
        ).isEqualTo("FANTACALCIO");
    }

    @Test
    void shouldExcludePlayerAfterNewerOfficialExitFromSerieA() {

        OffsetDateTime fantacalcioObservedAt =
                OffsetDateTime.parse(
                        "2024-08-01T09:00:00+02:00"
                );

        long fantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        1,
                        fantacalcioObservedAt
                );

        long gazzettaBatchId =
                createBatch(
                        "GAZZETTA",
                        1,
                        fantacalcioObservedAt
                );

        long fantamarktBatchId =
                createBatch(
                        "FANTAMARKT",
                        1,
                        fantacalcioObservedAt
                );

        Long playerId =
                createPlayer(
                        "Leaving Serie A Player"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        createMapping(
                playerId,
                "FANTACALCIO",
                "FC-EXIT",
                "Leaving Serie A Player"
        );

        insertRow(
                fantacalcioBatchId,
                1,
                "FC-EXIT",
                "Leaving Serie A Player",
                "Roma",
                "A"
        );

        insertRow(
                gazzettaBatchId,
                1,
                "GA-EXIT",
                "Leaving Serie A Player",
                "Roma",
                "A"
        );

        insertRow(
                fantamarktBatchId,
                1,
                "FM-EXIT",
                "Leaving Serie A Player",
                "Roma",
                "A"
        );

        createOfficialExitClaim(
                playerId,
                "Roma",
                "Club Estero Test",
                OffsetDateTime.parse(
                        "2024-08-06T10:00:00+02:00"
                )
        );

        ReconciliationResponse response =
                service.reconcile("2024/2025");

        assertThat(response.matchedPlayers())
                .isZero();

        String status =
                registrationStatus(playerId);

        assertThat(status)
                .isEqualTo("RELEASED");

        Integer reconciliationCount =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_reconciliation_results result
                        JOIN seasons season
                            ON season.id =
                               result.season_id
                        WHERE result.player_id = :playerId
                          AND season.code = '2024/2025'
                        """)
                        .param("playerId", playerId)
                        .query(Integer.class)
                        .single();

        assertThat(reconciliationCount)
                .isZero();

        PageResponse<PlayerCatalogResponse> catalog =
                playerCatalogService.findPlayers(
                        "2024/2025",
                        "Leaving Serie A Player",
                        null,
                        null,
                        0,
                        25
                );

        assertThat(catalog.totalElements())
                .isZero();
    }

    @Test
    void shouldDeactivatePlayerMissingFromLatestFantacalcioAndReactivateWhenHeReturns() {

        OffsetDateTime firstObservedAt =
                OffsetDateTime.parse(
                        "2024-08-01T10:00:00+02:00"
                );

        OffsetDateTime secondObservedAt =
                OffsetDateTime.parse(
                        "2024-08-02T10:00:00+02:00"
                );

        OffsetDateTime thirdObservedAt =
                OffsetDateTime.parse(
                        "2024-08-03T10:00:00+02:00"
                );

        /*
         * Secondary source is intentionally unrelated.
         * The lifecycle player must therefore prove that a
         * Fantacalcio-only player is operational.
         */
        long gazzettaBatchId =
                createBatch(
                        "GAZZETTA",
                        1,
                        firstObservedAt
                );

        insertRow(
                gazzettaBatchId,
                1,
                "GA-LIFECYCLE-OTHER",
                "Lifecycle Secondary Other",
                "Inter",
                "C"
        );

        /*
         * SNAPSHOT 1:
         * player appears on Fantacalcio -> ACTIVE.
         */
        long firstFantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        1,
                        firstObservedAt
                );

        insertRow(
                firstFantacalcioBatchId,
                1,
                "FC-LIFECYCLE-001",
                "Fantacalcio Lifecycle Player",
                "Roma",
                "A"
        );

        ReconciliationResponse firstResponse =
                service.reconcile("2024/2025");

        assertThat(firstResponse.matchedPlayers())
                .isEqualTo(1);

        Long originalPlayerId =
                findPlayerId(
                        "fantacalcio lifecycle player"
                );

        assertThat(registrationStatus(originalPlayerId))
                .isEqualTo("ACTIVE");

        assertThat(currentClub(originalPlayerId))
                .isEqualTo("Roma");

        Integer mappingCountBefore =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_source_mappings
                        WHERE player_id = :playerId
                          AND source = 'FANTACALCIO'
                          AND external_id =
                              'FC-LIFECYCLE-001'
                        """)
                        .param(
                                "playerId",
                                originalPlayerId
                        )
                        .query(Integer.class)
                        .single();

        assertThat(mappingCountBefore)
                .isEqualTo(1);

        /*
         * SNAPSHOT 2:
         * lifecycle player disappears from Fantacalcio.
         *
         * Another real Fantacalcio row keeps the snapshot
         * non-empty. The missing player must become INACTIVE,
         * never deleted.
         */
        long secondFantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        1,
                        secondObservedAt
                );

        insertRow(
                secondFantacalcioBatchId,
                1,
                "FC-LIFECYCLE-SENTINEL",
                "Fantacalcio Lifecycle Sentinel",
                "Lecce",
                "D"
        );

        service.reconcile("2024/2025");

        assertThat(registrationStatus(originalPlayerId))
                .isEqualTo("INACTIVE");

        Integer playerCountWhileInactive =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM players
                        WHERE id = :playerId
                        """)
                        .param(
                                "playerId",
                                originalPlayerId
                        )
                        .query(Integer.class)
                        .single();

        assertThat(playerCountWhileInactive)
                .isEqualTo(1);

        Integer mappingCountWhileInactive =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_source_mappings
                        WHERE player_id = :playerId
                          AND source = 'FANTACALCIO'
                          AND external_id =
                              'FC-LIFECYCLE-001'
                        """)
                        .param(
                                "playerId",
                                originalPlayerId
                        )
                        .query(Integer.class)
                        .single();

        assertThat(mappingCountWhileInactive)
                .isEqualTo(1);

        PageResponse<PlayerCatalogResponse> inactiveCatalog =
                playerCatalogService.findPlayers(
                        "2024/2025",
                        "Fantacalcio Lifecycle Player",
                        null,
                        null,
                        0,
                        25
                );

        assertThat(inactiveCatalog.totalElements())
                .isZero();

        /*
         * SNAPSHOT 3:
         * same Fantacalcio external ID returns.
         *
         * It must reactivate the SAME player, not create
         * a duplicate.
         */
        long thirdFantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        2,
                        thirdObservedAt
                );

        insertRow(
                thirdFantacalcioBatchId,
                1,
                "FC-LIFECYCLE-001",
                "Fantacalcio Lifecycle Player",
                "Roma",
                "A"
        );

        insertRow(
                thirdFantacalcioBatchId,
                2,
                "FC-LIFECYCLE-SENTINEL",
                "Fantacalcio Lifecycle Sentinel",
                "Lecce",
                "D"
        );

        service.reconcile("2024/2025");

        Long playerIdAfterReturn =
                findPlayerId(
                        "fantacalcio lifecycle player"
                );

        assertThat(playerIdAfterReturn)
                .isEqualTo(originalPlayerId);

        assertThat(registrationStatus(originalPlayerId))
                .isEqualTo("ACTIVE");

        assertThat(currentClub(originalPlayerId))
                .isEqualTo("Roma");

        Integer playerCountAfterReturn =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM players
                        WHERE normalized_name =
                              'fantacalcio lifecycle player'
                        """)
                        .query(Integer.class)
                        .single();

        assertThat(playerCountAfterReturn)
                .isEqualTo(1);

        Integer mappingCountAfterReturn =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_source_mappings
                        WHERE player_id = :playerId
                          AND source = 'FANTACALCIO'
                          AND external_id =
                              'FC-LIFECYCLE-001'
                        """)
                        .param(
                                "playerId",
                                originalPlayerId
                        )
                        .query(Integer.class)
                        .single();

        assertThat(mappingCountAfterReturn)
                .isEqualTo(1);

        PageResponse<PlayerCatalogResponse> activeCatalog =
                playerCatalogService.findPlayers(
                        "2024/2025",
                        "Fantacalcio Lifecycle Player",
                        null,
                        null,
                        0,
                        25
                );

        assertThat(activeCatalog.totalElements())
                .isEqualTo(1);

        assertThat(activeCatalog.content())
                .extracting(
                        PlayerCatalogResponse::id
                )
                .containsExactly(
                        originalPlayerId
                );
    }
    @Test
    void shouldKeepFantacalcioOutOfGamePlayerInactiveAcrossReconciliations() {

        OffsetDateTime firstObservedAt =
                OffsetDateTime.parse(
                        "2024-08-20T10:00:00+02:00"
                );

        OffsetDateTime secondObservedAt =
                OffsetDateTime.parse(
                        "2024-08-21T10:00:00+02:00"
                );

        Long playerId =
                createPlayer(
                        "Out Of Game Persistent Player"
                );

        registerPlayer(
                playerId,
                "Atalanta"
        );

        createMapping(
                playerId,
                "FANTACALCIO",
                "FC-OUT-OF-GAME-PERSISTENT",
                "Out Of Game Persistent Player"
        );

        /*
         * Reconciliation currently expects a latest Gazzetta snapshot.
         * Its player is intentionally unrelated to the lifecycle player.
         */
        long gazzettaBatchId =
                createBatch(
                        "GAZZETTA",
                        1,
                        firstObservedAt
                );

        insertRow(
                gazzettaBatchId,
                1,
                "GA-OUT-OF-GAME-OTHER",
                "Out Of Game Secondary Other",
                "Inter",
                "C"
        );

        /*
         * SNAPSHOT 1:
         * Fantacalcio still contains the player but explicitly marks
         * him as out_of_game.
         */
        long firstFantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        1,
                        firstObservedAt
                );

        insertRow(
                firstFantacalcioBatchId,
                1,
                "FC-OUT-OF-GAME-PERSISTENT",
                "Out Of Game Persistent Player",
                "Atalanta",
                "D"
        );

        markImportRowOutOfGame(
                firstFantacalcioBatchId,
                "FC-OUT-OF-GAME-PERSISTENT"
        );

        ReconciliationResponse firstResponse =
                service.reconcile("2024/2025");

        assertThat(firstResponse.status())
                .isEqualTo("COMPLETED");

        assertThat(registrationStatus(playerId))
                .isEqualTo("INACTIVE");

        assertThat(playerActive(playerId))
                .isTrue();

        assertThat(registrationLeftAtIsNull(playerId))
                .isTrue();

        assertThat(reconciliationCount(playerId))
                .isZero();

        /*
         * SNAPSHOT 2:
         * the same player is still present in Fantacalcio and still
         * carries out_of_game=true.
         *
         * Reconciliation must NOT reactivate him merely because the
         * Fantacalcio row still exists and still names Atalanta.
         */
        long secondFantacalcioBatchId =
                createBatch(
                        "FANTACALCIO",
                        1,
                        secondObservedAt
                );

        insertRow(
                secondFantacalcioBatchId,
                1,
                "FC-OUT-OF-GAME-PERSISTENT",
                "Out Of Game Persistent Player",
                "Atalanta",
                "D"
        );

        markImportRowOutOfGame(
                secondFantacalcioBatchId,
                "FC-OUT-OF-GAME-PERSISTENT"
        );

        ReconciliationResponse secondResponse =
                service.reconcile("2024/2025");

        assertThat(secondResponse.status())
                .isEqualTo("COMPLETED");

        assertThat(registrationStatus(playerId))
                .isEqualTo("INACTIVE");

        assertThat(playerActive(playerId))
                .isTrue();

        assertThat(registrationLeftAtIsNull(playerId))
                .isTrue();

        assertThat(reconciliationCount(playerId))
                .isZero();

        Integer mappingCount =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_source_mappings
                        WHERE player_id = :playerId
                          AND source = 'FANTACALCIO'
                          AND external_id =
                              'FC-OUT-OF-GAME-PERSISTENT'
                        """)
                        .param("playerId", playerId)
                        .query(Integer.class)
                        .single();

        assertThat(mappingCount)
                .isEqualTo(1);
    }

    private void markImportRowOutOfGame(
            long batchId,
            String externalId
    ) {

        int updated =
                jdbcClient.sql("""
                        UPDATE player_import_rows
                        SET raw_data =
                            COALESCE(
                                raw_data,
                                '{}'::jsonb
                            )
                            || '{"out_of_game":"true"}'::jsonb
                        WHERE batch_id = :batchId
                          AND external_id = :externalId
                        """)
                        .param("batchId", batchId)
                        .param("externalId", externalId)
                        .update();

        assertThat(updated)
                .isEqualTo(1);
    }

    private boolean playerActive(
            Long playerId
    ) {

        return jdbcClient.sql("""
                SELECT active
                FROM players
                WHERE id = :playerId
                """)
                .param("playerId", playerId)
                .query(Boolean.class)
                .single();
    }

    private boolean registrationLeftAtIsNull(
            Long playerId
    ) {

        return jdbcClient.sql("""
                SELECT registration.left_at IS NULL
                FROM player_registrations registration
                JOIN seasons season
                    ON season.id =
                       registration.season_id
                WHERE registration.player_id = :playerId
                  AND season.code = '2024/2025'
                """)
                .param("playerId", playerId)
                .query(Boolean.class)
                .single();
    }

    private int reconciliationCount(
            Long playerId
    ) {

        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM player_reconciliation_results result
                JOIN seasons season
                    ON season.id = result.season_id
                WHERE result.player_id = :playerId
                  AND season.code = '2024/2025'
                """)
                .param("playerId", playerId)
                .query(Integer.class)
                .single();
    }
    private long createBatch(
            String source,
            int totalRows,
            OffsetDateTime sourceObservedAt
    ) {

        return jdbcClient.sql("""
                INSERT INTO import_batches (
                    source,
                    season_id,
                    original_filename,
                    status,
                    total_rows,
                    valid_rows,
                    invalid_rows,
                    source_reference,
                    source_observed_at,
                    completed_at
                )
                SELECT
                    :source,
                    season.id,
                    :filename,
                    'COMPLETED',
                    :totalRows,
                    :totalRows,
                    0,
                    :sourceReference,
                    :sourceObservedAt,
                    CURRENT_TIMESTAMP
                FROM seasons season
                WHERE season.code = '2024/2025'
                RETURNING id
                """)
                .param("source", source)
                .param(
                        "filename",
                        "reconciliation-v2-"
                                + source.toLowerCase()
                                + ".csv"
                )
                .param("totalRows", totalRows)
                .param(
                        "sourceReference",
                        "https://source.test/"
                                + source.toLowerCase()
                )
                .param(
                        "sourceObservedAt",
                        sourceObservedAt
                )
                .query(Long.class)
                .single();
    }

    private void insertRow(
            long batchId,
            int rowNumber,
            String externalId,
            String name,
            String club,
            String role
    ) {

        String normalizedName =
                name.toLowerCase();

        String normalizedClub =
                club.toLowerCase();

        jdbcClient.sql("""
                INSERT INTO player_import_rows (
                    batch_id,
                    row_number,
                    external_id,
                    external_name,
                    normalized_name,
                    club_name,
                    normalized_club_name,
                    fantasy_role,
                    quotation,
                    fantasy_market_value,
                    valid,
                    raw_data
                )
                VALUES (
                    :batchId,
                    :rowNumber,
                    :externalId,
                    :externalName,
                    :normalizedName,
                    :clubName,
                    :normalizedClubName,
                    :fantasyRole,
                    :quotation,
                    :fantasyMarketValue,
                    TRUE,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param("batchId", batchId)
                .param("rowNumber", rowNumber)
                .param("externalId", externalId)
                .param("externalName", name)
                .param("normalizedName", normalizedName)
                .param("clubName", club)
                .param(
                        "normalizedClubName",
                        normalizedClub
                )
                .param("fantasyRole", role)
                .param(
                        "quotation",
                        rowNumber * 5
                )
                .param(
                        "fantasyMarketValue",
                        rowNumber * 40
                )
                .param(
                        "rawData",
                        """
                        {
                          "reconciliationV2Test": true
                        }
                        """
                )
                .update();
    }

    private Long createPlayer(
            String name
    ) {

        return jdbcClient.sql("""
                INSERT INTO players (
                    last_name,
                    display_name,
                    normalized_name,
                    active
                )
                VALUES (
                    :name,
                    :name,
                    :normalizedName,
                    TRUE
                )
                RETURNING id
                """)
                .param("name", name)
                .param(
                        "normalizedName",
                        name.toLowerCase()
                )
                .query(Long.class)
                .single();
    }

    private void registerPlayer(
            Long playerId,
            String club
    ) {

        jdbcClient.sql("""
                INSERT INTO player_registrations (
                    player_id,
                    club_id,
                    season_id,
                    registration_status
                )
                SELECT
                    :playerId,
                    club.id,
                    season.id,
                    'ACTIVE'
                FROM clubs club
                CROSS JOIN seasons season
                WHERE club.name = :club
                  AND season.code = '2024/2025'
                """)
                .param("playerId", playerId)
                .param("club", club)
                .update();
    }

    private void createMapping(
            Long playerId,
            String source,
            String externalId,
            String externalName
    ) {

        jdbcClient.sql("""
                INSERT INTO player_source_mappings (
                    player_id,
                    source,
                    external_id,
                    external_name,
                    verified
                )
                VALUES (
                    :playerId,
                    :source,
                    :externalId,
                    :externalName,
                    TRUE
                )
                """)
                .param("playerId", playerId)
                .param("source", source)
                .param("externalId", externalId)
                .param("externalName", externalName)
                .update();
    }

    private void createOfficialTransferClaim(
            Long playerId,
            String fromClub,
            String toClub,
            String source,
            OffsetDateTime publishedAt
    ) {

        jdbcClient.sql("""
                INSERT INTO player_transfer_claims (
                    player_id,
                    season_id,
                    from_club_id,
                    to_club_id,
                    destination_club_name,
                    source,
                    source_reference,
                    source_published_at,
                    observed_at,
                    effective_at,
                    verification_status,
                    verified_at
                )
                SELECT
                    :playerId,
                    season.id,
                    from_club.id,
                    to_club.id,
                    to_club.name,
                    :source,
                    :sourceReference,
                    :publishedAt,
                    :publishedAt,
                    CAST(:publishedAt AS TIMESTAMP WITH TIME ZONE)::date,
                    'OFFICIAL',
                    :publishedAt
                FROM seasons season
                JOIN clubs from_club
                    ON from_club.name = :fromClub
                JOIN clubs to_club
                    ON to_club.name = :toClub
                WHERE season.code = '2024/2025'
                """)
                .param("playerId", playerId)
                .param("source", source)
                .param(
                        "sourceReference",
                        "https://official.test/transfer"
                )
                .param("publishedAt", publishedAt)
                .param("fromClub", fromClub)
                .param("toClub", toClub)
                .update();
    }

    private void createOfficialExitClaim(
            Long playerId,
            String fromClub,
            String destination,
            OffsetDateTime publishedAt
    ) {

        jdbcClient.sql("""
                INSERT INTO player_transfer_claims (
                    player_id,
                    season_id,
                    from_club_id,
                    to_club_id,
                    destination_club_name,
                    source,
                    source_reference,
                    source_published_at,
                    observed_at,
                    effective_at,
                    verification_status,
                    verified_at
                )
                SELECT
                    :playerId,
                    season.id,
                    from_club.id,
                    NULL,
                    :destination,
                    'FIGC',
                    :sourceReference,
                    :publishedAt,
                    :publishedAt,
                    CAST(:publishedAt AS TIMESTAMP WITH TIME ZONE)::date,
                    'OFFICIAL',
                    :publishedAt
                FROM seasons season
                JOIN clubs from_club
                    ON from_club.name = :fromClub
                WHERE season.code = '2024/2025'
                """)
                .param("playerId", playerId)
                .param("destination", destination)
                .param(
                        "sourceReference",
                        "https://official.test/exit"
                )
                .param("publishedAt", publishedAt)
                .param("fromClub", fromClub)
                .update();
    }

    private Long findPlayerId(
            String normalizedName
    ) {

        return jdbcClient.sql("""
                SELECT id
                FROM players
                WHERE normalized_name = :normalizedName
                ORDER BY id DESC
                LIMIT 1
                """)
                .param(
                        "normalizedName",
                        normalizedName
                )
                .query(Long.class)
                .single();
    }

    private String currentClub(
            Long playerId
    ) {

        return jdbcClient.sql("""
                SELECT club.name
                FROM player_registrations registration
                JOIN clubs club
                    ON club.id =
                       registration.club_id
                JOIN seasons season
                    ON season.id =
                       registration.season_id
                WHERE registration.player_id = :playerId
                  AND season.code = '2024/2025'
                """)
                .param("playerId", playerId)
                .query(String.class)
                .single();
    }

    private String registrationStatus(
            Long playerId
    ) {

        return jdbcClient.sql("""
                SELECT registration.registration_status
                FROM player_registrations registration
                JOIN seasons season
                    ON season.id =
                       registration.season_id
                WHERE registration.player_id = :playerId
                  AND season.code = '2024/2025'
                """)
                .param("playerId", playerId)
                .query(String.class)
                .single();
    }

    private String reconciliationField(
            Long playerId,
            String field
    ) {

        if (!field.equals(
                "identity_resolution_method"
        ) && !field.equals(
                "club_resolution_source"
        )) {
            throw new IllegalArgumentException(
                    "Campo test non consentito"
            );
        }

        return jdbcClient.sql(
                """
                SELECT %s
                FROM player_reconciliation_results result
                JOIN seasons season
                    ON season.id = result.season_id
                WHERE result.player_id = :playerId
                  AND season.code = '2024/2025'
                """.formatted(field)
        )
                .param("playerId", playerId)
                .query(String.class)
                .single();
    }
}
