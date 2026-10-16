package it.fantacalciointelligence.backend.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class TransferVerificationConcurrencyTest {

    @Autowired
    private TransferVerificationService service;

    @Autowired
    private JdbcClient jdbcClient;

    private final List<Long> playerIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long playerId : playerIds) {
            jdbcClient.sql("DELETE FROM player_registration_events WHERE player_id = :playerId")
                    .param("playerId", playerId)
                    .update();
            jdbcClient.sql("DELETE FROM player_transfer_claims WHERE player_id = :playerId")
                    .param("playerId", playerId)
                    .update();
            jdbcClient.sql("DELETE FROM player_registrations WHERE player_id = :playerId")
                    .param("playerId", playerId)
                    .update();
            jdbcClient.sql("DELETE FROM players WHERE id = :playerId")
                    .param("playerId", playerId)
                    .update();
        }
    }

    @Test
    void concurrentVerificationOfSameClaimCreatesOneEvent() throws Exception {
        Long playerId = createRegisteredPlayer("Concurrent Same Claim Player");
        Long claimId = createClaim(playerId, "Bologna", "snapshot:concurrent-same");
        OfficialTransferVerificationRequest evidence = new OfficialTransferVerificationRequest(
                "OFFICIAL_CLUB",
                "official:concurrent-same",
                null,
                LocalDate.of(2026, 8, 20)
        );
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<TransferClaimResponse> first = executor.submit(() -> {
                start.await();
                return service.verifyOfficial(claimId, evidence);
            });
            Future<TransferClaimResponse> second = executor.submit(() -> {
                start.await();
                return service.verifyOfficial(claimId, evidence);
            });
            start.countDown();

            assertThat(first.get().verificationStatus()).isEqualTo("OFFICIAL");
            assertThat(second.get().verificationStatus()).isEqualTo("OFFICIAL");
        }

        assertThat(jdbcClient.sql("""
                SELECT COUNT(*) FROM player_registration_events
                WHERE transfer_claim_id = :claimId
                """)
                .param("claimId", claimId)
                .query(Integer.class)
                .single())
                .isEqualTo(1);
        assertThat(currentClub(playerId)).isEqualTo("Bologna");
    }

    @Test
    void concurrentConflictingClaimsCannotBothMutateRegistration() throws Exception {
        Long playerId = createRegisteredPlayer("Concurrent Conflicting Claims Player");
        Long firstClaimId = createClaim(playerId, "Bologna", "snapshot:conflict-one");
        Long secondClaimId = createClaim(playerId, "Napoli", "snapshot:conflict-two");
        OfficialTransferVerificationRequest evidence = new OfficialTransferVerificationRequest(
                "FIGC",
                "official:concurrent-conflict",
                null,
                LocalDate.of(2026, 8, 21)
        );
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> verifyAfterLatch(
                    start,
                    firstClaimId,
                    evidence
            ));
            Future<Boolean> second = executor.submit(() -> verifyAfterLatch(
                    start,
                    secondClaimId,
                    evidence
            ));
            start.countDown();

            assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(jdbcClient.sql("""
                SELECT verification_status
                FROM player_transfer_claims
                WHERE id IN (:firstClaimId, :secondClaimId)
                ORDER BY id
                """)
                .param("firstClaimId", firstClaimId)
                .param("secondClaimId", secondClaimId)
                .query(String.class)
                .list())
                .containsExactlyInAnyOrder("OFFICIAL", "REJECTED");
        assertThat(jdbcClient.sql("""
                SELECT COUNT(*) FROM player_registration_events
                WHERE player_id = :playerId
                """)
                .param("playerId", playerId)
                .query(Integer.class)
                .single())
                .isEqualTo(1);
        assertThat(currentClub(playerId)).isIn("Bologna", "Napoli");
    }

    private boolean verifyAfterLatch(
            CountDownLatch start,
            Long claimId,
            OfficialTransferVerificationRequest evidence
    ) throws InterruptedException {
        start.await();
        try {
            service.verifyOfficial(claimId, evidence);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Long createRegisteredPlayer(String name) {
        Long playerId = jdbcClient.sql("""
                INSERT INTO players(last_name, display_name, normalized_name, active)
                VALUES (:name, :name, LOWER(:name), TRUE)
                RETURNING id
                """)
                .param("name", name)
                .query(Long.class)
                .single();
        playerIds.add(playerId);
        jdbcClient.sql("""
                INSERT INTO player_registrations(
                    player_id, club_id, season_id, registration_status
                )
                SELECT :playerId, club.id, season.id, 'ACTIVE'
                FROM clubs club
                CROSS JOIN seasons season
                WHERE club.name = 'Roma' AND season.code = '2026/2027'
                """)
                .param("playerId", playerId)
                .update();
        return playerId;
    }

    private Long createClaim(Long playerId, String targetClub, String reference) {
        return service.createClaim(new TransferClaimRequest(
                playerId,
                "2026/2027",
                targetClub,
                false,
                null,
                "FANTACALCIO",
                reference,
                null,
                null
        )).id();
    }

    private String currentClub(Long playerId) {
        return jdbcClient.sql("""
                SELECT club.name
                FROM player_registrations registration
                JOIN clubs club ON club.id = registration.club_id
                JOIN seasons season ON season.id = registration.season_id
                WHERE registration.player_id = :playerId
                  AND season.code = '2026/2027'
                """)
                .param("playerId", playerId)
                .query(String.class)
                .single();
    }
}
