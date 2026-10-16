package it.fantacalciointelligence.backend.squad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SquadOwnershipHttpIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder encoder;

    private Long leagueId;

    @BeforeEach
    void fixture() {
        jdbc.sql("DELETE FROM fantasy_squads WHERE owner_username IN ('owner-a','owner-b','disabled-owner')")
                .update();
        jdbc.sql("DELETE FROM app_users WHERE username IN ('owner-a','owner-b','disabled-owner','legacy-owner')")
                .update();
        createUser("owner-a", true);
        createUser("owner-b", true);
        createUser("disabled-owner", false);
        leagueId = jdbc.sql("SELECT id FROM league_configurations WHERE active = TRUE")
                .query(Long.class).single();
    }

    @Test
    void createsForPrincipalAndEnforcesMultiUserOwnership() throws Exception {
        String body = """
                {"leagueConfigurationId":%d,"name":"Rosa A","active":true}
                """.formatted(leagueId);

        String response = mockMvc.perform(post("/api/v1/squads")
                        .with(httpBasic("owner-a", "password-owner-a"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rosa A"))
                .andReturn().getResponse().getContentAsString();

        Long squadId = jdbc.sql("SELECT id FROM fantasy_squads WHERE owner_username='owner-a'")
                .query(Long.class).single();
        assertThat(response).contains(squadId.toString());

        mockMvc.perform(get("/api/v1/squad").with(httpBasic("owner-a", "password-owner-a")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(squadId));
        mockMvc.perform(get("/api/v1/squads").with(httpBasic("owner-a", "password-owner-a")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(squadId));
        mockMvc.perform(get("/api/v1/squads/{id}", squadId)
                        .with(httpBasic("owner-b", "password-owner-b")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/squads/{id}", squadId)
                        .with(httpBasic("admin-test", "admin-pass")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/squad-analysis"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/squad-analysis")
                        .with(httpBasic("owner-a", "password-owner-a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.squadId").value(squadId));
        mockMvc.perform(get("/api/v1/squad-analysis/squads/{id}", squadId)
                        .with(httpBasic("owner-b", "password-owner-b")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/squad-analysis/squads/{id}", squadId)
                        .with(httpBasic("admin-test", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.squadId").value(squadId));
        mockMvc.perform(get("/api/v1/squad")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/squad")
                        .with(httpBasic("disabled-owner", "password-disabled-owner")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void databaseRejectsNewSquadWithoutOwner() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO fantasy_squads (league_configuration_id,name,initial_budget,active)
                VALUES (:league,'Owner mancante',1000,FALSE)
                """).param("league", leagueId).update()).isInstanceOf(RuntimeException.class);
    }

    @Test
    void preventsCreatingAuctionSessionForAnotherUsersSquad() throws Exception {
        createSquad("owner-a", "Rosa privata");
        Long squadId = jdbc.sql("SELECT id FROM fantasy_squads WHERE owner_username='owner-a'")
                .query(Long.class).single();

        mockMvc.perform(post("/api/v1/auction/sessions")
                        .with(httpBasic("owner-b", "password-owner-b"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"squadId\":%d}".formatted(squadId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void lineupRequiresAuthenticationAndEnforcesSquadOwnership() throws Exception {
        // Test-seeded active squad with 23 players, owner 'legacy-owner'.
        Long ownedSquadId = jdbc.sql("""
                SELECT squad.id
                FROM fantasy_squads squad
                WHERE squad.owner_username = 'legacy-owner'
                  AND squad.active = TRUE
                ORDER BY squad.id
                LIMIT 1
                """)
                .query(Long.class)
                .optional()
                .orElseThrow();
        createUser("legacy-owner", true);

        // Anonymous callers can no longer read a roster.
        mockMvc.perform(get("/api/v1/lineups/recommended")
                        .param("matchday", "1")
                        .param("season", "2026/2027")
                        .param("squadId", ownedSquadId.toString()))
                .andExpect(status().isUnauthorized());

        // Another user receives the same 404 as a non-existent squad (anti-IDOR).
        mockMvc.perform(get("/api/v1/lineups/recommended")
                        .with(httpBasic("owner-b", "password-owner-b"))
                        .param("matchday", "1")
                        .param("season", "2026/2027")
                        .param("squadId", ownedSquadId.toString()))
                .andExpect(status().isNotFound());

        // The owner can read the recommendation.
        mockMvc.perform(get("/api/v1/lineups/recommended")
                        .with(httpBasic("legacy-owner", "password-legacy-owner"))
                        .param("matchday", "1")
                        .param("season", "2026/2027")
                        .param("squadId", ownedSquadId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.squadId").value(ownedSquadId));
    }

    private void createSquad(String username, String name) throws Exception {
        mockMvc.perform(post("/api/v1/squads")
                        .with(httpBasic(username, "password-" + username))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"leagueConfigurationId\":%d,\"name\":\"%s\",\"active\":true}"
                                .formatted(leagueId, name)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialBudget").value(1000))
                .andExpect(jsonPath("$.maximumPlayers").value(25))
                .andExpect(jsonPath("$.totalPlayers").value(0));
    }

    private void createUser(String username, boolean enabled) {
        jdbc.sql("""
                INSERT INTO app_users(username,email,password_hash,role,enabled)
                VALUES(:username,:email,:password,'USER',:enabled)
                """)
                .param("username", username)
                .param("email", username + "@example.test")
                .param("password", encoder.encode("password-" + username))
                .param("enabled", enabled)
                .update();
    }
}
