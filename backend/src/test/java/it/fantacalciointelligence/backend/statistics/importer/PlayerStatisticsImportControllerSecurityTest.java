package it.fantacalciointelligence.backend.statistics.importer;

import static org.springframework.test.web.servlet
        .request.MockMvcRequestBuilders.multipart;

import static org.springframework.test.web.servlet
        .result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerStatisticsImportControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectStatisticsImportWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        multipart(
                                "/api/v1/admin/imports/statistics/csv"
                        )
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    @WithMockUser(
            username = "standard-user",
            roles = "USER"
    )
    void shouldRejectStatisticsImportForStandardUser()
            throws Exception {

        mockMvc.perform(
                        multipart(
                                "/api/v1/admin/imports/statistics/csv"
                        )
                )
                .andExpect(
                        status().isForbidden()
                );
    }
}