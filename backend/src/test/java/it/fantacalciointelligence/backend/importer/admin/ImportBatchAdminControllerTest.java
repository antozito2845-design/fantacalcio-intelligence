package it.fantacalciointelligence.backend.importer.admin;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ImportBatchAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImportBatchAdminService service;

    @Test
    void shouldRejectBatchListWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/admin/imports"
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void shouldRejectBatchListForStandardUser()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/admin/imports"
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
    void shouldReturnBatchListForAdministrator()
            throws Exception {

        ImportBatchSummaryResponse batch =
                batchSummary();

        when(
                service.findBatches(
                        "FANTACALCIO",
                        "2024/2025",
                        "COMPLETED",
                        0,
                        20
                )
        ).thenReturn(
                new ImportBatchPageResponse(
                        List.of(batch),
                        0,
                        20,
                        1,
                        1
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/admin/imports"
                        )
                                .param(
                                        "source",
                                        "FANTACALCIO"
                                )
                                .param(
                                        "season",
                                        "2024/2025"
                                )
                                .param(
                                        "status",
                                        "COMPLETED"
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
                        jsonPath("$.totalElements")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.content[0].id")
                                .value(500)
                )
                .andExpect(
                        jsonPath("$.content[0].source")
                                .value("FANTACALCIO")
                );
    }

    @Test
    void shouldRejectBatchDetailWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/admin/imports/500"
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void shouldRejectBatchDetailForStandardUser()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/admin/imports/500"
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
    void shouldReturnBatchDetailForAdministrator()
            throws Exception {

        ImportRowErrorResponse invalidRow =
                new ImportRowErrorResponse(
                        900L,
                        2,
                        "TEST-002",
                        "Giocatore Errato",
                        "Napoli",
                        null,
                        "Ruolo non valido",
                        """
                        {
                          "role": "X"
                        }
                        """
                );

        when(
                service.findBatch(
                        500L,
                        0,
                        50
                )
        ).thenReturn(
                new ImportBatchDetailResponse(
                        batchSummary(),
                        List.of(invalidRow),
                        0,
                        50,
                        1,
                        1
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/admin/imports/500"
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
                        jsonPath("$.batch.id")
                                .value(500)
                )
                .andExpect(
                        jsonPath("$.totalInvalidRows")
                                .value(1)
                )
                .andExpect(
                        jsonPath(
                                "$.invalidRows[0].validationError"
                        )
                                .value(
                                        "Ruolo non valido"
                                )
                );
    }

    private ImportBatchSummaryResponse batchSummary() {
        OffsetDateTime now =
                OffsetDateTime.now();

        return new ImportBatchSummaryResponse(
                500L,
                "FANTACALCIO",
                "2024/2025",
                "players.csv",
                "COMPLETED",
                2,
                1,
                1,
                0,
                now,
                now,
                null,
                null,
                null
        );
    }
}