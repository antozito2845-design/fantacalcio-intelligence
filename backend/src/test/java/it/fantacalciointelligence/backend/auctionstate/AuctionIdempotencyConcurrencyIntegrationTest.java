package it.fantacalciointelligence.backend.auctionstate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.CreateSessionRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PurchaseRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PurchaseResponse;
import it.fantacalciointelligence.backend.common.ConflictException;

@SpringBootTest
class AuctionIdempotencyConcurrencyIntegrationTest {

    @Autowired
    AuctionStateService state;

    @Autowired
    AuctionPurchaseService purchases;

    @Autowired
    JdbcClient jdbc;

    Long sessionId;
    Long playerId;
    Long squadId;

    List<Long> fixtureRolePlayers = List.of();

    @BeforeEach
    void setup() {

        cleanup();

        fixtureRolePlayers =
                B59TestFixture.addFantacalcioRoles(jdbc);

        squadId =
                B59TestFixture.createSquad(jdbc);

        sessionId =
                state.activate(
                        state.create(
                                new CreateSessionRequest(squadId),
                                "idempotency-owner"
                        ).id()
                ).id();

        playerId =
                jdbc.sql("""
                        SELECT player_id
                        FROM auction_player_states
                        WHERE session_id = :session
                          AND availability = 'AVAILABLE'
                        ORDER BY player_id
                        LIMIT 1
                        """)
                        .param("session", sessionId)
                        .query(Long.class)
                        .single();
    }

    @AfterEach
    void tearDown() {

        cleanup();

        if (squadId != null) {
            jdbc.sql("""
                    DELETE FROM fantasy_squads
                    WHERE id = :id
                    """)
                    .param("id", squadId)
                    .update();
        }

        if (!fixtureRolePlayers.isEmpty()) {
            jdbc.sql("""
                    DELETE FROM player_roles
                    WHERE source = 'FANTACALCIO'
                      AND player_id IN (:ids)
                    """)
                    .param("ids", fixtureRolePlayers)
                    .update();
        }
    }

    @Test
    void concurrentSameIdempotencySamePayloadProducesSinglePurchase() throws Exception {

        PurchaseRequest request =
                new PurchaseRequest(
                        playerId,
                        AuctionBuyerType.OTHER,
                        null,
                        "same-key"
                );

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        var executor =
                Executors.newFixedThreadPool(2);

        List<Future<Object>> futures =
                new ArrayList<>();

        futures.add(
                executor.submit(
                        () -> run(
                                ready,
                                start,
                                request
                        )
                )
        );

        futures.add(
                executor.submit(
                        () -> run(
                                ready,
                                start,
                                request
                        )
                )
        );

        ready.await();
        start.countDown();

        int success = 0;
        int conflict = 0;

        for (Future<Object> future : futures) {

            Object result =
                    future.get();

            if (result instanceof PurchaseResponse) {
                success++;
            }

            if (result instanceof ConflictException) {
                conflict++;
            }
        }

        executor.shutdown();

        assertThat(success + conflict)
                .isEqualTo(2);

        assertThat(success)
                .isGreaterThanOrEqualTo(1);

        assertThat(
                jdbc.sql("""
                        SELECT COUNT(*)
                        FROM auction_purchases
                        WHERE session_id = :session
                          AND idempotency_key = 'same-key'
                        """)
                        .param("session", sessionId)
                        .query(Integer.class)
                        .single()
        )
                .isEqualTo(1);

        assertThat(
                jdbc.sql("""
                        SELECT COUNT(*)
                        FROM auction_purchases
                        WHERE session_id = :session
                          AND player_id = :player
                        """)
                        .param("session", sessionId)
                        .param("player", playerId)
                        .query(Integer.class)
                        .single()
        )
                .isEqualTo(1);
    }

    @Test
    void concurrentSameIdempotencyDifferentPayloadAllowsOnlyOneStoredPayload() throws Exception {

        PurchaseRequest first =
                new PurchaseRequest(
                        playerId,
                        AuctionBuyerType.OTHER,
                        null,
                        "shared-key"
                );

        PurchaseRequest second =
                new PurchaseRequest(
                        playerId,
                        AuctionBuyerType.OTHER,
                        2,
                        "shared-key"
                );

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        var executor =
                Executors.newFixedThreadPool(2);

        List<Future<Object>> futures =
                List.of(
                        executor.submit(
                                () -> run(
                                        ready,
                                        start,
                                        first
                                )
                        ),
                        executor.submit(
                                () -> run(
                                        ready,
                                        start,
                                        second
                                )
                        )
                );

        ready.await();
        start.countDown();

        int success = 0;
        int conflict = 0;

        for (Future<Object> future : futures) {

            Object result =
                    future.get();

            if (result instanceof PurchaseResponse) {
                success++;
            }

            if (result instanceof ConflictException) {
                conflict++;
            }
        }

        executor.shutdown();

        assertThat(success)
                .isEqualTo(1);

        assertThat(conflict)
                .isEqualTo(1);

        assertThat(
                jdbc.sql("""
                        SELECT COUNT(*)
                        FROM auction_purchases
                        WHERE session_id = :session
                          AND idempotency_key = 'shared-key'
                        """)
                        .param("session", sessionId)
                        .query(Integer.class)
                        .single()
        )
                .isEqualTo(1);
    }

    private Object run(
            CountDownLatch ready,
            CountDownLatch start,
            PurchaseRequest request
    ) {

        try {

            ready.countDown();
            start.await();

            return purchases.purchase(
                    sessionId,
                    request
            );

        } catch (ConflictException exception) {

            return exception;

        } catch (Exception exception) {

            return exception;
        }
    }

    private void cleanup() {

        jdbc.sql("DELETE FROM auction_events")
                .update();

        jdbc.sql("DELETE FROM auction_purchases")
                .update();

        jdbc.sql("DELETE FROM auction_player_states")
                .update();

        jdbc.sql("DELETE FROM auction_sessions")
                .update();
    }
}