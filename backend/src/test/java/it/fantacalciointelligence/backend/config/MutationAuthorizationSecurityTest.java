package it.fantacalciointelligence.backend.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MutationAuthorizationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void leagueReadRemainsPublic()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/league/configuration")
                )
                .andExpect(
                        status().is2xxSuccessful()
                );
    }

    @Test
    void leagueUpdateRequiresAuthentication()
            throws Exception {

        mockMvc.perform(
                        put("/api/v1/league/configuration")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void leagueUpdateRejectsStandardUser()
            throws Exception {

        mockMvc.perform(
                        put("/api/v1/league/configuration")
                                .with(
                                        httpBasic(
                                                "user-test",
                                                "user-pass"
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    void leagueUpdateAllowsAdminToReachController()
            throws Exception {

        mockMvc.perform(
                        put("/api/v1/league/configuration")
                                .with(
                                        httpBasic(
                                                "admin-test",
                                                "admin-pass"
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    @Test
    void squadReadRequiresAuthentication()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/squad")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void squadAddRequiresAuthentication()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/squad/players")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void squadAddAllowsStandardUserToReachController()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/squad/players")
                                .with(
                                        httpBasic(
                                                "user-test",
                                                "user-pass"
                                        )
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    @Test
    void squadDeleteRequiresAuthentication()
            throws Exception {

        mockMvc.perform(
                        delete("/api/v1/squad/players/999999")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void squadDeleteAllowsStandardUserToReachController()
            throws Exception {

        mockMvc.perform(
                        delete("/api/v1/squad/players/999999")
                                .with(
                                        httpBasic(
                                                "user-test",
                                                "user-pass"
                                        )
                                )
                )
                .andExpect(
                        status().isNotFound()
                );
    }

    @Test
    void publicScoutingReadRemainsPublic()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/scouting")
                )
                .andExpect(
                        status().is2xxSuccessful()
                );
    }

    @Test
    void adminScoutingMutationRequiresAuthentication()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/admin/scouting/players/999999"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    void adminScoutingMutationRejectsStandardUser()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/admin/scouting/players/999999"
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
                                .content("{}")
                )
                .andExpect(
                        status().isForbidden()
                );
    }
}
