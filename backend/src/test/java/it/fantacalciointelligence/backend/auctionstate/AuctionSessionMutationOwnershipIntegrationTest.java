package it.fantacalciointelligence.backend.auctionstate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.CreateSessionRequest;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuctionSessionMutationOwnershipIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    AuctionStateService state;

    @Autowired
    JdbcClient jdbc;

    Long sessionId;
    Long availablePlayer;

    @BeforeEach
    void fixture() {

        B59TestFixture.addFantacalcioRoles(jdbc);

        Long squad =
                B59TestFixture.createSquad(jdbc);

        var created =
                state.create(
                        new CreateSessionRequest(squad),
                        "owner-a"
                );

        var active =
                state.activate(created.id());

        sessionId = active.id();

        availablePlayer =
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

    @Test
    void differentUserCannotNominateOnForeignSession() throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auction/sessions/"
                                        + sessionId
                                        + "/nomination"
                        )
                                .with(
                                        user("owner-b")
                                                .roles("USER")
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "playerId": %d,
                                          "currentPrice": 1
                                        }
                                        """.formatted(availablePlayer))
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void differentUserCannotPurchaseOnForeignSession() throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auction/sessions/"
                                        + sessionId
                                        + "/purchases"
                        )
                                .with(
                                        user("owner-b")
                                                .roles("USER")
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "playerId": %d,
                                          "buyerType": "OTHER",
                                          "idempotencyKey": "foreign-user-test"
                                        }
                                        """.formatted(availablePlayer))
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void differentUserCannotCompleteForeignSession() throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auction/sessions/"
                                        + sessionId
                                        + "/complete"
                        )
                                .with(
                                        user("owner-b")
                                                .roles("USER")
                                )
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void differentUserCannotUseDecisionOnForeignSession() throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auction/sessions/"
                                        + sessionId
                                        + "/decision"
                        )
                                .with(
                                        user("owner-b")
                                                .roles("USER")
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "proposedBid": 1
                                        }
                                        """)
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void ownerCanNominateOnOwnSession() throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auction/sessions/"
                                        + sessionId
                                        + "/nomination"
                        )
                                .with(
                                        user("owner-a")
                                                .roles("USER")
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "playerId": %d,
                                          "currentPrice": 1
                                        }
                                        """.formatted(availablePlayer))
                )
                .andExpect(
                        status().isOk()
                );
    }

    @Test
    void adminCanOperateOnForeignSession() throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auction/sessions/"
                                        + sessionId
                                        + "/nomination"
                        )
                                .with(
                                        user("administrator")
                                                .roles("ADMIN")
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "playerId": %d,
                                          "currentPrice": 1
                                        }
                                        """.formatted(availablePlayer))
                )
                .andExpect(
                        status().isOk()
                );
    }
}