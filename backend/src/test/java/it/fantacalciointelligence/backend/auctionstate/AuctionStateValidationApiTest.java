package it.fantacalciointelligence.backend.auctionstate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuctionStateValidationApiTest {

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

    @Test
    void malformedJsonReturnsCleanBadRequest() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"squadId\":")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("Corpo richiesta JSON non valido")
                );
    }

    @Test
    void missingSquadIdReturnsBadRequest() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("Richiesta non valida")
                );
    }

    @Test
    void negativeSquadIdReturnsBadRequest() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"squadId\":-1}")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidBuyerEnumReturnsCleanBadRequest() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions/1/purchases")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "playerId": 1,
                                          "buyerType": "INVALID",
                                          "paidPrice": 1,
                                          "idempotencyKey": "validation-test"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("Corpo richiesta JSON non valido")
                );
    }

    @Test
    void blankIdempotencyKeyReturnsBadRequest() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions/1/purchases")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "playerId": 1,
                                          "buyerType": "OTHER",
                                          "idempotencyKey": "   "
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void tooLongIdempotencyKeyReturnsBadRequest() throws Exception {

        String key = "x".repeat(121);

        mvc.perform(
                        post("/api/v1/auction/sessions/1/purchases")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "playerId": 1,
                                          "buyerType": "OTHER",
                                          "idempotencyKey": "%s"
                                        }
                                        """.formatted(key))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidDecisionBoundsReturnBadRequest() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions/1/decision")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "proposedBid": 0,
                                          "currentMatchday": 39,
                                          "horizon": 11,
                                          "alternativeLimit": 21
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidNominationReturnsBadRequest() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions/1/nomination")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "playerId": 0,
                                          "currentPrice": 0
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());
    }
}