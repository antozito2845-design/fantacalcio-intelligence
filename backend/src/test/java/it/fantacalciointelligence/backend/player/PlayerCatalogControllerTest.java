package it.fantacalciointelligence.backend.player;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PlayerCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldReturnPublicPlayerCatalogWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/players")
                                .param("season", "2026/2027")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(
                        jsonPath("$.totalElements")
                                .value(isA(Number.class))
                )
                .andExpect(
                        jsonPath("$.totalPages")
                                .value(isA(Number.class))
                );
    }

    @Test
    void shouldReturnPlayerDetailWithoutAuthentication()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/players/{playerId}", 1)
                                .param("season", "2026/2027")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(
                        jsonPath("$.name")
                                .value(not(blankOrNullString()))
                )
                .andExpect(
                        jsonPath("$.club")
                                .value(not(blankOrNullString()))
                )
                .andExpect(
                        jsonPath("$.clubSlug")
                                .value(not(blankOrNullString()))
                )
                .andExpect(jsonPath("$.roles").isMap())
                .andExpect(jsonPath("$.statistics").isArray());
    }

    @Test
    void shouldUseDefaultSeasonForPlayerDetail()
            throws Exception {

        mockMvc.perform(
                        get("/api/v1/players/{playerId}", 1)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(
                        jsonPath("$.name")
                                .value(not(blankOrNullString()))
                );
    }

    @Test
    @Transactional
    void shouldReturnHistoricalStatisticsFromPreviousSeasons()
            throws Exception {

        Map<String, Object> currentPlayer = jdbcClient.sql("""
                SELECT pr.player_id, pr.club_id, current_season.code
                FROM player_registrations pr
                JOIN seasons current_season ON current_season.id = pr.season_id
                WHERE current_season.current_season = TRUE
                ORDER BY pr.player_id
                LIMIT 1
                """).query().singleRow();

        Map<String, Object> previousSeason = jdbcClient.sql("""
                SELECT id, code
                FROM seasons
                WHERE current_season = FALSE
                ORDER BY start_year DESC
                LIMIT 1
                """).query().singleRow();

        long playerId = ((Number) currentPlayer.get("player_id")).longValue();
        long clubId = ((Number) currentPlayer.get("club_id")).longValue();
        long previousSeasonId = ((Number) previousSeason.get("id")).longValue();
        String currentSeasonCode = currentPlayer.get("code").toString();
        String previousSeasonCode = previousSeason.get("code").toString();

        jdbcClient.sql("""
                INSERT INTO player_season_statistics (
                    player_id, club_id, season_id, source, appearances, starts,
                    minutes_played, goals, assists
                ) VALUES (
                    :playerId, :clubId, :seasonId, 'INTERNAL', 7, 5, 450, 2, 1
                )
                ON CONFLICT (player_id, season_id, source) DO UPDATE SET
                    appearances = EXCLUDED.appearances,
                    starts = EXCLUDED.starts,
                    minutes_played = EXCLUDED.minutes_played,
                    goals = EXCLUDED.goals,
                    assists = EXCLUDED.assists
                """)
                .param("playerId", playerId)
                .param("clubId", clubId)
                .param("seasonId", previousSeasonId)
                .update();

        mockMvc.perform(
                        get("/api/v1/players/{playerId}", playerId)
                                .param("season", currentSeasonCode)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistics[?(@.season == '%s')]", previousSeasonCode).isNotEmpty());
    }

    @Test
    void shouldReturnNotFoundForUnknownPlayer()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/players/{playerId}",
                                999999
                        )
                                .param("season", "2026/2027")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Giocatore non trovato")
                )
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
