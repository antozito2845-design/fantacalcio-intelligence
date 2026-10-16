package it.fantacalciointelligence.backend.freshness;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DataFreshnessControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectFreshnessWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/admin/data-freshness"
                        )
                                .param(
                                        "season",
                                        "2026/2027"
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
    void shouldRejectFreshnessForStandardUser()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/admin/data-freshness"
                        )
                                .param(
                                        "season",
                                        "2026/2027"
                                )
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    @WithMockUser(
            username = "administrator",
            roles = "ADMIN"
    )
    void shouldAllowFreshnessForAdministrator()
            throws Exception {

        prepareFreshProductionImports();

        mockMvc.perform(
                        get(
                                "/api/v1/admin/data-freshness"
                        )
                                .param(
                                        "season",
                                        "2026/2027"
                                )
                )
                .andExpect(
                        status().isOk()
                );
    }

    private void prepareFreshProductionImports() {
        for (String source :
                new String[]{
                        "FANTACALCIO",
                        "GAZZETTA",
                        "FANTAMARKT"
                }) {

            jdbcClient().sql("""
                    INSERT INTO import_batches (
                        source,
                        season_id,
                        original_filename,
                        status,
                        total_rows,
                        valid_rows,
                        invalid_rows,
                        completed_at,
                        source_observed_at
                    )

                    SELECT
                        :source,
                        season.id,
                        :filename,
                        'COMPLETED',
                        1,
                        1,
                        0,
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP

                    FROM seasons season
                    WHERE season.code = '2026/2027'
                    """)
                    .param("source", source)
                    .param(
                            "filename",
                            "security-freshness-"
                                    + source
                                    + ".csv"
                    )
                    .update();
        }
    }

    @Autowired
    private org.springframework.jdbc.core.simple.JdbcClient jdbcClient;

    private org.springframework.jdbc.core.simple.JdbcClient jdbcClient() {
        return jdbcClient;
    }
}