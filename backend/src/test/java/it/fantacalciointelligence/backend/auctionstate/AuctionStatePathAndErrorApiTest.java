package it.fantacalciointelligence.backend.auctionstate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuctionStatePathAndErrorApiTest {

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
    void nonNumericSessionIdReturnsBadRequest() throws Exception {

        mvc.perform(
                        get("/api/v1/auction/sessions/not-a-number")
                                .with(httpBasic("user-test", "user-pass"))
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("Parametro richiesta non valido")
                );
    }

    @Test
    void nonNumericSessionIdOnMutationReturnsBadRequest() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions/not-a-number/complete")
                                .with(httpBasic("user-test", "user-pass"))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedDatabaseErrorReturnsGeneric500WithoutLeak() throws Exception {

        doThrow(
                new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint secret_constraint"
                )
        )
                .when(access)
                .requireAccess(
                        any(),
                        any()
                );

        mvc.perform(
                        get("/api/v1/auction/sessions/1")
                                .with(httpBasic("user-test", "user-pass"))
                )
                .andExpect(status().isInternalServerError())
                .andExpect(
                        jsonPath("$.message")
                                .value("Errore interno del server")
                );
    }

    @Test
    void unexpectedRuntimeErrorReturnsGeneric500WithoutLeak() throws Exception {

        doThrow(
                new RuntimeException(
                        "jdbc:postgresql://secret-host/internal"
                )
        )
                .when(access)
                .requireAccess(
                        any(),
                        any()
                );

        mvc.perform(
                        get("/api/v1/auction/sessions/1/state")
                                .with(httpBasic("user-test", "user-pass"))
                )
                .andExpect(status().isInternalServerError())
                .andExpect(
                        jsonPath("$.message")
                                .value("Errore interno del server")
                );
    }

    @Test
    void malformedPurchaseBodyStillReturns400BeforeServiceExecution() throws Exception {

        mvc.perform(
                        post("/api/v1/auction/sessions/1/purchases")
                                .with(httpBasic("user-test", "user-pass"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message")
                                .value("Corpo richiesta JSON non valido")
                );
    }
}