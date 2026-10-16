package it.fantacalciointelligence.backend.transfer;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TransferVerificationServiceTest {

    @Autowired
    private TransferVerificationService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldApplyOfficialSerieATransferAndPreserveHistory() {

        Long playerId =
                createPlayer(
                        "Transfer Test Player"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        TransferClaimResponse detected =
                service.createClaim(
                        new TransferClaimRequest(
                                playerId,
                                "2024/2025",
                                "Bologna",
                                false,
                                null,
                                "OFFICIAL_CLUB",
                                "https://official.example/transfer",
                                OffsetDateTime.now(),
                                LocalDate.of(
                                        2024,
                                        8,
                                        10
                                )
                        )
                );

        assertThat(
                detected.verificationStatus()
        ).isEqualTo("DETECTED");

        String clubBeforeVerification =
                currentClub(playerId);

        assertThat(clubBeforeVerification)
                .isEqualTo("Roma");

        TransferClaimResponse official =
                service.verifyOfficial(
                        detected.id()
                );

        assertThat(
                official.verificationStatus()
        ).isEqualTo("OFFICIAL");

        assertThat(
                currentClub(playerId)
        ).isEqualTo("Bologna");

        String registrationStatus =
                jdbcClient.sql("""
                        SELECT registration.registration_status
                        FROM player_registrations registration
                        JOIN seasons season
                            ON season.id =
                               registration.season_id
                        WHERE registration.player_id = :playerId
                          AND season.code = '2024/2025'
                        """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .query(String.class)
                        .single();

        assertThat(registrationStatus)
                .isEqualTo("ACTIVE");

        Integer historyCount =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_registration_events
                        WHERE player_id = :playerId
                          AND transfer_claim_id = :claimId
                        """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .param(
                                "claimId",
                                detected.id()
                        )
                        .query(Integer.class)
                        .single();

        assertThat(historyCount)
                .isEqualTo(1);
    }

    @Test
    void shouldNotAllowSecondarySourceToBecomeOfficialDirectly() {

        Long playerId =
                createPlayer(
                        "Secondary Source Test Player"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        TransferClaimResponse detected =
                service.createClaim(
                        new TransferClaimRequest(
                                playerId,
                                "2024/2025",
                                "Bologna",
                                false,
                                null,
                                "GAZZETTA",
                                "https://example.test/gazzetta",
                                OffsetDateTime.now(),
                                LocalDate.of(
                                        2024,
                                        8,
                                        11
                                )
                        )
                );

        assertThatThrownBy(() ->
                service.verifyOfficial(
                        detected.id()
                )
        )
                .isInstanceOf(
                        ConflictException.class
                )
                .hasMessageContaining(
                        "non e' una fonte ufficiale"
                );

        assertThat(
                currentClub(playerId)
        ).isEqualTo("Roma");
    }

    @Test
    void shouldMarkPlayerAsReleasedWhenOfficiallyLeavingSerieA() {

        Long playerId =
                createPlayer(
                        "Serie A Exit Test Player"
                );

        registerPlayer(
                playerId,
                "Roma"
        );

        TransferClaimResponse detected =
                service.createClaim(
                        new TransferClaimRequest(
                                playerId,
                                "2024/2025",
                                null,
                                true,
                                "Club Estero Test",
                                "FIGC",
                                "https://official.example/exit",
                                OffsetDateTime.now(),
                                LocalDate.of(
                                        2024,
                                        8,
                                        12
                                )
                        )
                );

        service.verifyOfficial(
                detected.id()
        );

        String status =
                jdbcClient.sql("""
                        SELECT registration.registration_status
                        FROM player_registrations registration
                        JOIN seasons season
                            ON season.id =
                               registration.season_id
                        WHERE registration.player_id = :playerId
                          AND season.code = '2024/2025'
                        """)
                        .param(
                                "playerId",
                                playerId
                        )
                        .query(String.class)
                        .single();

        assertThat(status)
                .isEqualTo("RELEASED");

        String eventType =
                jdbcClient.sql("""
                        SELECT event_type
                        FROM player_registration_events
                        WHERE transfer_claim_id = :claimId
                        """)
                        .param(
                                "claimId",
                                detected.id()
                        )
                        .query(String.class)
                        .single();

        assertThat(eventType)
                .isEqualTo("LEFT_SERIE_A");

        assertThat(jdbcClient.sql("SELECT active FROM players WHERE id = :playerId")
                .param("playerId", playerId)
                .query(Boolean.class)
                .single())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIGC", "LEGA_SERIE_A", "OFFICIAL_CLUB"})
    void autoDetectedClaimCanBeConfirmedOnSameClaim(String officialSource) {
        Long playerId = createPlayer("Detected Same Claim " + officialSource);
        registerPlayer(playerId, "Roma");
        TransferClaimResponse detected = createDetectedClaim(playerId, "FANTACALCIO", null);

        TransferClaimResponse official = service.verifyOfficial(
                detected.id(),
                officialEvidence(officialSource)
        );

        assertThat(official.id()).isEqualTo(detected.id());
        assertThat(official.verificationStatus()).isEqualTo("OFFICIAL");
        assertThat(official.source()).isEqualTo("FANTACALCIO");
        assertThat(official.sourceReference()).isEqualTo("snapshot:test");
        assertThat(official.verificationSource()).isEqualTo(officialSource);
        assertThat(official.verificationReference()).isEqualTo("official:test");
        assertThat(currentClub(playerId)).isEqualTo("Bologna");
    }

    @Test
    void pendingClaimCanBecomeOfficialAndRetryIsIdempotent() {
        Long playerId = createPlayer("Pending Retry Player");
        registerPlayer(playerId, "Roma");
        TransferClaimResponse detected = createDetectedClaim(playerId, "FANTACALCIO", null);
        jdbcClient.sql("""
                UPDATE player_transfer_claims
                SET verification_status = 'PENDING_VERIFICATION'
                WHERE id = :claimId
                """)
                .param("claimId", detected.id())
                .update();

        service.verifyOfficial(detected.id(), officialEvidence("OFFICIAL_CLUB"));
        service.verifyOfficial(detected.id(), officialEvidence("FIGC"));

        assertThat(jdbcClient.sql("""
                SELECT COUNT(*) FROM player_registration_events
                WHERE transfer_claim_id = :claimId
                """)
                .param("claimId", detected.id())
                .query(Integer.class)
                .single())
                .isEqualTo(1);
        assertThat(service.findById(detected.id()).verificationSource())
                .isEqualTo("OFFICIAL_CLUB");
    }

    @Test
    void officialEvidenceMustBeCompleteAndOfficial() {
        Long playerId = createPlayer("Evidence Validation Player");
        registerPlayer(playerId, "Roma");
        Long claimId = createDetectedClaim(playerId, "FANTACALCIO", null).id();

        assertThatThrownBy(() -> service.verifyOfficial(
                claimId,
                new OfficialTransferVerificationRequest(
                        "FANTACALCIO", "reference", null, LocalDate.of(2024, 8, 20)
                )
        )).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.verifyOfficial(
                claimId,
                new OfficialTransferVerificationRequest(
                        "FIGC", " ", null, LocalDate.of(2024, 8, 20)
                )
        )).isInstanceOf(BadRequestException.class);

        assertThatThrownBy(() -> service.verifyOfficial(
                claimId,
                new OfficialTransferVerificationRequest("FIGC", "reference", null, null)
        )).isInstanceOf(BadRequestException.class);
        assertThat(currentClub(playerId)).isEqualTo("Roma");
    }

    @Test
    void targetMustBelongToRequestedSeason() {
        Long playerId = createPlayer("Outside Membership Player");
        registerPlayer(playerId, "Roma");
        jdbcClient.sql("""
                INSERT INTO clubs(name, short_name, slug, active)
                VALUES ('Outside Season Club', 'Outside', 'outside-season-club', TRUE)
                """).update();

        assertThatThrownBy(() -> service.createClaim(new TransferClaimRequest(
                playerId,
                "2024/2025",
                "Outside Season Club",
                false,
                null,
                "FANTACALCIO",
                "snapshot:test",
                null,
                null
        ))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void staleRegistrationPreconditionRollsBackOfficialConfirmation() {
        Long playerId = createPlayer("Stale Registration Player");
        registerPlayer(playerId, "Roma");
        Long claimId = createDetectedClaim(playerId, "FANTACALCIO", null).id();
        jdbcClient.sql("""
                UPDATE player_registrations registration
                SET club_id = club.id
                FROM clubs club, seasons season
                WHERE registration.player_id = :playerId
                  AND registration.season_id = season.id
                  AND season.code = '2024/2025'
                  AND club.name = 'Bologna'
                """)
                .param("playerId", playerId)
                .update();

        assertThatThrownBy(() -> service.verifyOfficial(
                claimId,
                officialEvidence("FIGC")
        )).isInstanceOf(ConflictException.class);
        assertThat(service.findById(claimId).verificationStatus()).isEqualTo("DETECTED");
        assertThat(jdbcClient.sql("""
                SELECT COUNT(*) FROM player_registration_events
                WHERE transfer_claim_id = :claimId
                """)
                .param("claimId", claimId)
                .query(Integer.class)
                .single())
                .isZero();
    }

    @Test
    void officialClaimRejectsCompetingOpenClaimsFromSameState() {
        Long playerId = createPlayer("Competing Claims Player");
        registerPlayer(playerId, "Roma");
        Long officialClaimId = createDetectedClaim(playerId, "FANTACALCIO", null).id();
        Long competingClaimId = createDetectedClaim(playerId, "GAZZETTA", null).id();

        service.verifyOfficial(officialClaimId, officialEvidence("LEGA_SERIE_A"));

        TransferClaimResponse competing = service.findById(competingClaimId);
        assertThat(competing.verificationStatus()).isEqualTo("REJECTED");
        assertThat(competing.rejectionReason())
                .isEqualTo("Superseded by official transfer claim " + officialClaimId);
        assertThatThrownBy(() -> service.verifyOfficial(
                competingClaimId,
                officialEvidence("FIGC")
        )).isInstanceOf(ConflictException.class);
    }

    @Test
    void officialClaimCannotBeRejected() {
        Long playerId = createPlayer("Official Reject Player");
        registerPlayer(playerId, "Roma");
        Long claimId = createDetectedClaim(playerId, "FANTACALCIO", null).id();
        service.verifyOfficial(claimId, officialEvidence("FIGC"));

        assertThatThrownBy(() -> service.reject(claimId, "invalid"))
                .isInstanceOf(ConflictException.class);
    }

    private TransferClaimResponse createDetectedClaim(
            Long playerId,
            String source,
            LocalDate effectiveAt
    ) {
        return service.createClaim(new TransferClaimRequest(
                playerId,
                "2024/2025",
                "Bologna",
                false,
                null,
                source,
                "snapshot:test",
                null,
                effectiveAt
        ));
    }

    private OfficialTransferVerificationRequest officialEvidence(String source) {
        return new OfficialTransferVerificationRequest(
                source,
                "official:test",
                null,
                LocalDate.of(2024, 8, 20)
        );
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
                INSERT INTO season_clubs (season_id, club_id)
                SELECT season.id, candidate.id
                FROM seasons season
                JOIN clubs candidate ON candidate.name IN (:currentClub, 'Bologna')
                WHERE season.code = '2024/2025'
                ON CONFLICT DO NOTHING
                """)
                .param("currentClub", club)
                .update();

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
                .param(
                        "playerId",
                        playerId
                )
                .param("club", club)
                .update();
    }

    private String currentClub(
            Long playerId
    ) {
        return jdbcClient.sql("""
                SELECT club.name
                FROM player_registrations registration
                JOIN clubs club
                    ON club.id = registration.club_id
                JOIN seasons season
                    ON season.id =
                       registration.season_id
                WHERE registration.player_id = :playerId
                  AND season.code = '2024/2025'
                """)
                .param(
                        "playerId",
                        playerId
                )
                .query(String.class)
                .single();
    }
}
