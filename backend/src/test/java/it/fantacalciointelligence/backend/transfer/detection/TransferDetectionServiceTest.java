package it.fantacalciointelligence.backend.transfer.detection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class TransferDetectionServiceTest {

    @Autowired
    private TransferDetectionService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldDetectFantacalcioClubChange() {

        Long playerId =
                createPlayer(
                        "Auto Transfer Player One",
                        "FC-AUTO-001"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        long fc =
                createBatch(
                        "FANTACALCIO"
                );

        insertRow(
                fc,
                "FC-AUTO-001",
                "Auto Transfer Player One",
                "Bologna"
        );

        TransferDetectionResponse response =
                service.detect(
                        fc,
                        null,
                        null
                );

        assertThat(response.detectedClubChanges())
                .isEqualTo(1);

        assertThat(response.createdClaims())
                .isEqualTo(1);

        String status =
                jdbcClient.sql("""
                        SELECT verification_status
                        FROM player_transfer_claims
                        WHERE id = :claimId
                        """)
                        .param(
                                "claimId",
                                response.createdClaimIds()
                                        .getFirst()
                        )
                        .query(String.class)
                        .single();

        assertThat(status)
                .isEqualTo("DETECTED");
    }

    @Test
    void shouldMarkDetectionPendingWhenSecondaryCorroborates() {

        Long playerId =
                createPlayer(
                        "Auto Transfer Player Two",
                        "FC-AUTO-002"
                );

        addMapping(
                playerId,
                "GAZZETTA",
                "GA-AUTO-002",
                "Auto Transfer Player Two"
        );

        registerPlayer(
                playerId,
                "Roma"
        );

        long fc =
                createBatch(
                        "FANTACALCIO"
                );

        long ga =
                createBatch(
                        "GAZZETTA"
                );

        insertRow(
                fc,
                "FC-AUTO-002",
                "Auto Transfer Player Two",
                "Bologna"
        );

        insertRow(
                ga,
                "GA-AUTO-002",
                "Auto Transfer Player Two",
                "Bologna"
        );

        TransferDetectionResponse response =
                service.detect(
                        fc,
                        ga,
                        null
                );

        assertThat(response.createdClaims())
                .isEqualTo(1);

        assertThat(response.corroboratedClaims())
                .isEqualTo(1);

        String status =
                jdbcClient.sql("""
                        SELECT verification_status
                        FROM player_transfer_claims
                        WHERE id = :claimId
                        """)
                        .param(
                                "claimId",
                                response.createdClaimIds()
                                        .getFirst()
                        )
                        .query(String.class)
                        .single();

        assertThat(status)
                .isEqualTo(
                        "PENDING_VERIFICATION"
                );
    }

    @Test
    void shouldIgnoreGloballyExistingClubOutsideBatchSeason() {
        Long playerId = createPlayer(
                "Outside Season Detection Player",
                "FC-OUTSIDE-SEASON"
        );
        registerPlayer(playerId, "Roma");
        jdbcClient.sql("""
                INSERT INTO clubs(name, short_name, slug, active)
                VALUES ('Detection Outside Club', 'Outside', 'detection-outside-club', TRUE)
                """).update();
        long batchId = createBatch("FANTACALCIO");
        insertRow(
                batchId,
                "FC-OUTSIDE-SEASON",
                "Outside Season Detection Player",
                "Detection Outside Club"
        );

        TransferDetectionResponse response = service.detect(batchId, null, null);

        assertThat(response.detectedClubChanges()).isZero();
        assertThat(response.createdClaims()).isZero();
        assertThat(jdbcClient.sql("""
                SELECT COUNT(*) FROM player_transfer_claims
                WHERE player_id = :playerId
                """)
                .param("playerId", playerId)
                .query(Integer.class)
                .single())
                .isZero();
    }


    @Test
    void shouldDetectFantacalcioOutOfGameAsSerieAExit() {

        Long playerId =
                createPlayer(
                        "Out Of Game Player One",
                        "FC-OUT-GAME-001"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        long fc =
                createBatch(
                        "FANTACALCIO"
                );

        insertRow(
                fc,
                "FC-OUT-GAME-001",
                "Out Of Game Player One",
                "Roma"
        );

        markOutOfGame(
                fc,
                "FC-OUT-GAME-001"
        );

        TransferDetectionResponse response =
                service.detect(
                        fc,
                        null,
                        null
                );

        assertThat(response.detectedSerieAExits())
                .isEqualTo(1);

        assertThat(response.createdClaims())
                .isEqualTo(1);

        Long claimId =
                response.createdClaimIds()
                        .getFirst();

        var claim =
                jdbcClient.sql("""
                        SELECT
                            to_club_id,
                            destination_club_name,
                            verification_status,
                            source
                        FROM player_transfer_claims
                        WHERE id = :claimId
                        """)
                        .param(
                                "claimId",
                                claimId
                        )
                        .query()
                        .singleRow();

        assertThat(claim.get("to_club_id"))
                .isNull();

        assertThat(claim.get("destination_club_name"))
                .isNull();

        assertThat(claim.get("verification_status"))
                .isEqualTo("DETECTED");

        assertThat(claim.get("source"))
                .isEqualTo("FANTACALCIO");

        String registrationStatus =
                jdbcClient.sql("""
                        SELECT registration_status
                        FROM player_registrations
                        WHERE player_id = :playerId
                          AND season_id = (
                              SELECT id
                              FROM seasons
                              WHERE code = '2026/2027'
                          )
                        """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .query(String.class)
                        .single();

        /*
         * Detection alone must NOT invent RELEASED.
         * Reconciliation/operational guard handles exclusion,
         * official evidence later closes the lifecycle.
         */
        assertThat(registrationStatus)
                .isEqualTo("ACTIVE");
    }

    @Test
    void shouldNotDuplicateOpenFantacalcioOutOfGameExitClaim() {

        Long playerId =
                createPlayer(
                        "Out Of Game Player Two",
                        "FC-OUT-GAME-002"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        long fc =
                createBatch(
                        "FANTACALCIO"
                );

        insertRow(
                fc,
                "FC-OUT-GAME-002",
                "Out Of Game Player Two",
                "Roma"
        );

        markOutOfGame(
                fc,
                "FC-OUT-GAME-002"
        );

        TransferDetectionResponse first =
                service.detect(
                        fc,
                        null,
                        null
                );

        TransferDetectionResponse second =
                service.detect(
                        fc,
                        null,
                        null
                );

        assertThat(first.detectedSerieAExits())
                .isEqualTo(1);

        assertThat(first.createdClaims())
                .isEqualTo(1);

        assertThat(second.detectedSerieAExits())
                .isEqualTo(1);

        assertThat(second.createdClaims())
                .isZero();

        assertThat(second.existingOpenClaims())
                .isEqualTo(1);

        Integer claims =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_transfer_claims
                        WHERE player_id = :playerId
                          AND to_club_id IS NULL
                          AND verification_status IN (
                              'DETECTED',
                              'PENDING_VERIFICATION'
                          )
                        """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .query(Integer.class)
                        .single();

        assertThat(claims)
                .isEqualTo(1);
    }

    @Test
    void shouldIgnoreOutOfGamePlayerAlreadyInactive() {

        Long playerId =
                createPlayer(
                        "Out Of Game Player Three",
                        "FC-OUT-GAME-003"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        jdbcClient.sql("""
                UPDATE player_registrations
                SET registration_status = 'INACTIVE',
                    updated_at = CURRENT_TIMESTAMP
                WHERE player_id = :playerId
                  AND season_id = (
                      SELECT id
                      FROM seasons
                      WHERE code = '2026/2027'
                  )
                """)
                .param(
                        "playerId",
                        playerId
                )
                .update();

        long fc =
                createBatch(
                        "FANTACALCIO"
                );

        insertRow(
                fc,
                "FC-OUT-GAME-003",
                "Out Of Game Player Three",
                "Roma"
        );

        markOutOfGame(
                fc,
                "FC-OUT-GAME-003"
        );

        TransferDetectionResponse response =
                service.detect(
                        fc,
                        null,
                        null
                );

        assertThat(response.detectedSerieAExits())
                .isZero();

        assertThat(response.createdClaims())
                .isZero();

        Integer claims =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_transfer_claims
                        WHERE player_id = :playerId
                        """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .query(Integer.class)
                        .single();

        assertThat(claims)
                .isZero();

        String status =
                jdbcClient.sql("""
                        SELECT registration_status
                        FROM player_registrations
                        WHERE player_id = :playerId
                          AND season_id = (
                              SELECT id
                              FROM seasons
                              WHERE code = '2026/2027'
                          )
                        """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .query(String.class)
                        .single();

        assertThat(status)
                .isEqualTo("INACTIVE");
    }
    private Long createPlayer(
            String name,
            String externalId
    ) {

        Long playerId =
                jdbcClient.sql("""
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

        addMapping(
                playerId,
                "FANTACALCIO",
                externalId,
                name
        );

        return playerId;
    }

    private void addMapping(
            Long playerId,
            String source,
            String externalId,
            String name
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
                    :name,
                    TRUE
                )
                """)
                .param("playerId", playerId)
                .param("source", source)
                .param("externalId", externalId)
                .param("name", name)
                .update();
    }


    private void markOutOfGame(
            long batchId,
            String externalId
    ) {

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
                .param(
                        "batchId",
                        batchId
                )
                .param(
                        "externalId",
                        externalId
                )
                .update();
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
                  AND season.code = '2026/2027'
                """)
                .param("playerId", playerId)
                .param("club", club)
                .update();
    }

    private long createBatch(
            String source
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
                    completed_at,
                    source_observed_at
                )
                SELECT
                    :source,
                    season.id,
                    :filename,
                    'COMPLETED',
                    1,
                    1,
                    0,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                FROM seasons season
                WHERE season.code = '2026/2027'
                RETURNING id
                """)
                .param("source", source)
                .param(
                        "filename",
                        "transfer-detection-"
                                + source
                                + ".csv"
                )
                .query(Long.class)
                .single();
    }

    private void insertRow(
            long batchId,
            String externalId,
            String name,
            String club
    ) {

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
                    valid,
                    raw_data
                )
                VALUES (
                    :batchId,
                    1,
                    :externalId,
                    :name,
                    LOWER(:name),
                    :club,
                    LOWER(:club),
                    'C',
                    TRUE,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param("batchId", batchId)
                .param("externalId", externalId)
                .param("name", name)
                .param("club", club)
                .param(
                        "rawData",
                        """
                        {
                          "transferDetectionTest": true
                        }
                        """
                )
                .update();
    }
}
