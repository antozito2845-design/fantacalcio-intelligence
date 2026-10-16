package it.fantacalciointelligence.backend.config;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import it.fantacalciointelligence.backend.importer.PlayerImportResponse;
import it.fantacalciointelligence.backend.importer.PlayerImportService;
import it.fantacalciointelligence.backend.reconciliation.PlayerReconciliationService;
import it.fantacalciointelligence.backend.reconciliation.ReconciliationResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminEndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerImportService playerImportService;

    @MockitoBean
    private PlayerReconciliationService
            playerReconciliationService;

    @Test
    void shouldProtectCatalogQualityGateAsAdminEndpoint()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/admin/catalog/readiness")
                                .param("season", "2026/2027")
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        get("/api/v1/admin/catalog/readiness")
                                .param("season", "2026/2027")
                                .with(httpBasic("user-test", "user-pass"))
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectImportWithoutAuthentication()
            throws Exception {

        MockMultipartFile file = csvFile();

        mockMvc.perform(
                        multipart(
                                "/api/v1/imports/players/csv"
                        )
                                .file(file)
                                .param(
                                        "source",
                                        "FANTACALCIO"
                                )
                                .param(
                                        "season",
                                        "2024/2025"
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void shouldRejectImportForStandardUser()
            throws Exception {

        MockMultipartFile file = csvFile();

        mockMvc.perform(
                        multipart(
                                "/api/v1/imports/players/csv"
                        )
                                .file(file)
                                .param(
                                        "source",
                                        "FANTACALCIO"
                                )
                                .param(
                                        "season",
                                        "2024/2025"
                                )
                                .with(
                                        httpBasic(
                                                "user-test",
                                                "user-pass"
                                        )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void shouldAllowImportForAdministrator()
            throws Exception {

        MockMultipartFile file = csvFile();

        when(
                playerImportService.importCsv(
                        eq("FANTACALCIO"),
                        eq("2024/2025"),
                        any()
                )
        ).thenReturn(
                new PlayerImportResponse(
                        100L,
                        "FANTACALCIO",
                        "2024/2025",
                        "players.csv",
                        1,
                        1,
                        0,
                        "COMPLETED",
                        null
                )
        );

        mockMvc.perform(
                        multipart(
                                "/api/v1/imports/players/csv"
                        )
                                .file(file)
                                .param(
                                        "source",
                                        "FANTACALCIO"
                                )
                                .param(
                                        "season",
                                        "2024/2025"
                                )
                                .with(
                                        httpBasic(
                                                "admin-test",
                                                "admin-pass"
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("COMPLETED")
                )
                .andExpect(
                        jsonPath("$.batchId")
                                .value(100)
                );
    }

    @Test
    void shouldRejectReconciliationWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/reconciliation/players"
                        )
                                .param(
                                        "season",
                                        "2024/2025"
                                )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void shouldRejectReconciliationForStandardUser()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/reconciliation/players"
                        )
                                .param(
                                        "season",
                                        "2024/2025"
                                )
                                .with(
                                        httpBasic(
                                                "user-test",
                                                "user-pass"
                                        )
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void shouldAllowReconciliationForAdministrator()
            throws Exception {

        when(
                playerReconciliationService.reconcile(
                        "2024/2025"
                )
        ).thenReturn(
                new ReconciliationResponse(
                        "2024/2025",
                        101L,
                        102L,
                        103L,
                        4,
                        1,
                        3,
                        2,
                        2,
                        2,
                        "COMPLETED"
                )
        );

        mockMvc.perform(
                        post(
                                "/api/v1/reconciliation/players"
                        )
                                .param(
                                        "season",
                                        "2024/2025"
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .with(
                                        httpBasic(
                                                "admin-test",
                                                "admin-pass"
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value("COMPLETED")
                )
                .andExpect(
                        jsonPath("$.matchedPlayers")
                                .value(4)
                );
    }

    private MockMultipartFile csvFile() {
        return new MockMultipartFile(
                "file",
                "players.csv",
                "text/csv",
                """
                external_id,name,club,role
                TEST-001,Giocatore Test,Napoli,A
                """
                        .getBytes(
                                StandardCharsets.UTF_8
                        )
        );
    }
}
