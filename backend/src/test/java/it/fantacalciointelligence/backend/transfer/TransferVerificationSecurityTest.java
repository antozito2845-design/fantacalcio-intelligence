package it.fantacalciointelligence.backend.transfer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class TransferVerificationSecurityTest {

    private static final String OFFICIAL_REQUEST = """
            {
              "source": "FIGC",
              "sourceReference": "official:test",
              "effectiveAt": "2024-08-20"
            }
            """;

    private static final String CREATE_REQUEST = """
            {
              "playerId": 1,
              "season": "2024/2025",
              "targetClub": "Bologna",
              "leavingSerieA": false,
              "source": "FANTACALCIO",
              "sourceReference": "snapshot:test",
              "sourcePublishedAt": "2024-08-19T12:00:00Z",
              "effectiveAt": "2024-08-20"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferVerificationService service;

    @Test
    void anonymousCannotVerifyTransfer() throws Exception {
        mockMvc.perform(post("/api/v1/admin/transfers/1/verify-official")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFICIAL_REQUEST))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/transfers/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotVerifyTransfer() throws Exception {
        mockMvc.perform(post("/api/v1/admin/transfers/1/verify-official")
                        .with(httpBasic("user-test", "user-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFICIAL_REQUEST))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/transfers/claims")
                        .with(httpBasic("user-test", "user-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReachTransferMutationsAndPendingRead() throws Exception {
        when(service.findPending("2024/2025")).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/admin/transfers/claims")
                        .with(httpBasic("admin-test", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_REQUEST))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/transfers/1/verify-official")
                        .with(httpBasic("admin-test", "admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(OFFICIAL_REQUEST))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/transfers/1/reject")
                        .with(httpBasic("admin-test", "admin-pass"))
                        .param("reason", "invalid"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/transfers/pending")
                        .with(httpBasic("admin-test", "admin-pass"))
                        .param("season", "2024/2025"))
                .andExpect(status().isOk());
    }
}
