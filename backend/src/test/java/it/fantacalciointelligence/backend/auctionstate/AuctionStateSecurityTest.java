package it.fantacalciointelligence.backend.auctionstate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.SessionResponse;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionDecisionResponse;
import it.fantacalciointelligence.backend.squad.SquadService;

@SpringBootTest
@AutoConfigureMockMvc
class AuctionStateSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuctionStateService state;

    @MockitoBean
    AuctionPurchaseService purchases;

    @MockitoBean
    AuctionContextAdapter adapter;

    @MockitoBean
    AuctionSessionAccessService access;

    @MockitoBean
    SquadService squads;

    @Test
    void sessionMutationRequiresAuthentication() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"squadId\":1}")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void purchaseMutationRequiresAuthentication() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions/1/purchases")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "playerId": 2,
                                          "buyerType": "OTHER",
                                          "idempotencyKey": "x"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void authenticatedUserCanCreateSession() throws Exception {

        when(
                access.authenticatedUsername(
                        any(Authentication.class)
                )
        )
                .thenReturn("user-test");

        when(
                state.create(
                        any(),
                        eq("user-test")
                )
        )
                .thenReturn(session());

        mvc.perform(
                        post("/api/v1/auction/sessions")
                                .with(
                                        httpBasic(
                                                "user-test",
                                                "user-pass"
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"squadId\":1}"
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }

    @Test
    void sessionDecisionRequiresAuthentication() throws Exception {

        mvc.perform(
                        post(
                                "/api/v1/auction/sessions/1/decision"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"proposedBid\":5}"
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void authenticatedOwnerCanUseSessionDecision() throws Exception {

        doNothing()
                .when(access)
                .requireAccess(
                        eq(1L),
                        any(Authentication.class)
                );

        when(
                adapter.decide(
                        eq(1L),
                        any()
                )
        )
                .thenReturn(
                        org.mockito.Mockito.mock(
                                LiveAuctionDecisionResponse.class
                        )
                );

        mvc.perform(
                        post(
                                "/api/v1/auction/sessions/1/decision"
                        )
                                .with(
                                        httpBasic(
                                                "user-test",
                                                "user-pass"
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        "{\"proposedBid\":5}"
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }

    private SessionResponse session() {

        return new SessionResponse(
                1L,
                1L,
                "2026/2027",
                8,
                1000,
                false,
                AuctionSessionStatus.DRAFT,
                false,
                null,
                null,
                0,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );
    }
}
