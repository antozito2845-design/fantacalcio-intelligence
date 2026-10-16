package it.fantacalciointelligence.backend.scouting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class B70ArrivalScoutingIntegrationTest {

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private B70ArrivalScoutingService service;

    @Autowired
    private MockMvc mockMvc;

    private Long eligiblePlayerId;
    private Long ineligiblePlayerId;

    @BeforeEach
    void seed() {

        Long seasonId = jdbcClient.sql("""
                        SELECT id
                        FROM seasons
                        WHERE code = '2026/2027'
                        """)
                .query(Long.class)
                .single();

        eligiblePlayerId = jdbcClient.sql("""
                        SELECT player.id
                        FROM players player
                        JOIN player_registrations registration
                            ON registration.player_id =
                               player.id
                           AND registration.season_id =
                               :seasonId
                           AND registration.registration_status =
                               'ACTIVE'
                        JOIN season_clubs membership
                            ON membership.season_id =
                               :seasonId
                           AND membership.club_id =
                               registration.club_id
                        JOIN player_roles role
                            ON role.player_id = player.id
                           AND role.season_id = :seasonId
                           AND role.source = 'INTERNAL'
                        WHERE player.active = TRUE
                        ORDER BY player.id
                        LIMIT 1
                        """)
                .param("seasonId", seasonId)
                .query(Long.class)
                .single();

        ineligiblePlayerId = jdbcClient.sql("""
                        SELECT player.id
                        FROM players player
                        WHERE player.id <> :eligiblePlayerId
                        ORDER BY player.id
                        LIMIT 1
                        """)
                .param(
                        "eligiblePlayerId",
                        eligiblePlayerId
                )
                .query(Long.class)
                .single();

        jdbcClient.sql("""
                        INSERT INTO player_roles (
                            player_id,
                            season_id,
                            source,
                            fantasy_role,
                            primary_role
                        )
                        SELECT
                            role.player_id,
                            role.season_id,
                            'FANTACALCIO',
                            role.fantasy_role,
                            TRUE
                        FROM player_roles role
                        WHERE role.player_id =
                            :eligiblePlayerId
                          AND role.season_id =
                            :seasonId
                          AND role.source =
                            'INTERNAL'
                        ON CONFLICT (
                            player_id,
                            season_id,
                            source
                        )
                        DO UPDATE SET
                            fantasy_role =
                                EXCLUDED.fantasy_role,
                            primary_role = TRUE
                        """)
                .param(
                        "eligiblePlayerId",
                        eligiblePlayerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .update();

        jdbcClient.sql("""
                        INSERT INTO foreign_player_scouting_reports (
                            player_id,
                            season_id,
                            previous_club,
                            previous_league,
                            previous_country_code,
                            transfer_type,
                            adaptation_score,
                            potential_score,
                            risk_score,
                            confidence_score,
                            recommendation,
                            scouting_summary
                        )
                        VALUES (
                            :playerId,
                            :seasonId,
                            'Test Foreign Club',
                            'Test Foreign League',
                            'FR',
                            'PERMANENT',
                            82,
                            91,
                            24,
                            88,
                            'HIGH_PRIORITY',
                            'B70 eligible test scouting'
                        )
                        ON CONFLICT (
                            player_id,
                            season_id
                        )
                        DO UPDATE SET
                            previous_club =
                                EXCLUDED.previous_club,
                            previous_league =
                                EXCLUDED.previous_league,
                            previous_country_code =
                                EXCLUDED.previous_country_code,
                            transfer_type =
                                EXCLUDED.transfer_type,
                            adaptation_score =
                                EXCLUDED.adaptation_score,
                            potential_score =
                                EXCLUDED.potential_score,
                            risk_score =
                                EXCLUDED.risk_score,
                            confidence_score =
                                EXCLUDED.confidence_score,
                            recommendation =
                                EXCLUDED.recommendation,
                            scouting_summary =
                                EXCLUDED.scouting_summary
                        """)
                .param(
                        "playerId",
                        eligiblePlayerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .update();

        /*
         * Report exists, but this player intentionally has no
         * authoritative FANTACALCIO role prepared by this test.
         * Therefore it must never leak into the B70 arrival feed.
         */
        jdbcClient.sql("""
                        INSERT INTO foreign_player_scouting_reports (
                            player_id,
                            season_id,
                            previous_club,
                            previous_league,
                            previous_country_code,
                            transfer_type,
                            adaptation_score,
                            potential_score,
                            risk_score,
                            confidence_score,
                            recommendation,
                            scouting_summary
                        )
                        VALUES (
                            :playerId,
                            :seasonId,
                            'Hidden Foreign Club',
                            'Hidden Foreign League',
                            'DE',
                            'PERMANENT',
                            99,
                            99,
                            1,
                            99,
                            'HIGH_PRIORITY',
                            'Must not be exposed'
                        )
                        ON CONFLICT (
                            player_id,
                            season_id
                        )
                        DO UPDATE SET
                            previous_country_code = 'DE',
                            recommendation =
                                'HIGH_PRIORITY',
                            scouting_summary =
                                'Must not be exposed'
                        """)
                .param(
                        "playerId",
                        ineligiblePlayerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .update();
    }

    @Test
    void shouldExposeOnlyConfirmedSerieAPlayers() {

        List<B70ArrivalScoutingResponse> result =
                service.findCurrentArrivals(
                        "2026/2027",
                        null,
                        null,
                        null
                );

        assertThat(result)
                .extracting(
                        B70ArrivalScoutingResponse
                                ::playerId
                )
                .contains(
                        eligiblePlayerId
                )
                .doesNotContain(
                        ineligiblePlayerId
                );

        B70ArrivalScoutingResponse player =
                result.stream()
                        .filter(item ->
                                item.playerId()
                                        .equals(
                                                eligiblePlayerId
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                player.eligibilityStatus()
        ).isEqualTo(
                "CONFIRMED_SERIE_A"
        );

        assertThat(player.role())
                .isIn(
                        "P",
                        "D",
                        "C",
                        "A"
                );
    }

    @Test
    void shouldFilterRecommendation() {

        List<B70ArrivalScoutingResponse> result =
                service.findCurrentArrivals(
                        "2026/2027",
                        "high_priority",
                        null,
                        null
                );

        assertThat(result)
                .allSatisfy(item ->
                        assertThat(
                                item.recommendation()
                        ).isEqualTo(
                                "HIGH_PRIORITY"
                        )
                );
    }

    @Test
    void shouldRejectInvalidRole() {

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() ->
                        service.findCurrentArrivals(
                                "2026/2027",
                                null,
                                "X",
                                null
                        )
                )
                .isInstanceOf(
                        it.fantacalciointelligence
                                .backend
                                .common
                                .BadRequestException
                                .class
                );
    }

    @Test
    void shouldExposePublicEndpoint() throws Exception {

        mockMvc.perform(
                        get(
                                "/api/v1/scouting/arrivals"
                        )
                                .param(
                                        "season",
                                        "2026/2027"
                                )
                                .param(
                                        "recommendation",
                                        "HIGH_PRIORITY"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$").isArray()
                );
    }
}