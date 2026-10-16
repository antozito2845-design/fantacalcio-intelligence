package it.fantacalciointelligence.backend.refresh;

import static org.springframework.test.web.servlet
        .request.MockMvcRequestBuilders.post;

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
class DataRefreshControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectRefreshWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/admin/data-refresh/run"
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
    void shouldRejectRefreshForStandardUser()
            throws Exception {

        mockMvc.perform(
                        post(
                                "/api/v1/admin/data-refresh/run"
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
}