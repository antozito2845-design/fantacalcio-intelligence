package it.fantacalciointelligence.backend.development;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlayerDevelopmentAssessmentServiceTest {

    @Autowired
    private PlayerDevelopmentAssessmentService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldIdentifyPlayerWithGrowthPotential() {

        TestContext context =
                createOfficialPlayer(
                        "Development Growing Player",
                        "development growing player"
                );

        createStatistics(
                context.playerId(),
                context.clubId(),
                context.olderSeasonId(),
                12,
                6,
                600,
                "5.80",
                1,
                1,
                "1.20",
                "1.00"
        );

        createStatistics(
                context.playerId(),
                context.clubId(),
                context.latestSeasonId(),
                28,
                24,
                2200,
                "6.80",
                9,
                7,
                "8.00",
                "6.00"
        );

        service.generate("2026/2027");

        PlayerDevelopmentAssessmentResponse assessment =
                service.findByPlayer(
                        context.playerId(),
                        "2026/2027"
                );

        assertThat(assessment.trend())
                .isEqualTo("GROWING");

        assertThat(assessment.growthScore())
                .isGreaterThan(
                        new BigDecimal("62")
                );

        assertThat(assessment.declineRiskScore())
                .isLessThan(
                        new BigDecimal("48")
                );

        assertThat(assessment.analysisSummary())
                .contains(
                        "potrebbe fare il salto di qualità"
                )
                .contains(
                        "minutaggio in forte aumento"
                )
                .contains(
                        "fantamedia in crescita"
                );
    }

    @Test
    void shouldIdentifyPlayerAtRiskOfDecline() {

        TestContext context =
                createOfficialPlayer(
                        "Development Declining Player",
                        "development declining player"
                );

        createStatistics(
                context.playerId(),
                context.clubId(),
                context.olderSeasonId(),
                34,
                32,
                2800,
                "7.20",
                12,
                9,
                "11.00",
                "8.00"
        );

        createStatistics(
                context.playerId(),
                context.clubId(),
                context.latestSeasonId(),
                18,
                9,
                900,
                "5.70",
                2,
                1,
                "2.00",
                "1.00"
        );

        service.generate("2026/2027");

        PlayerDevelopmentAssessmentResponse assessment =
                service.findByPlayer(
                        context.playerId(),
                        "2026/2027"
                );

        assertThat(assessment.trend())
                .isEqualTo("DECLINING");

        assertThat(assessment.declineRiskScore())
                .isGreaterThanOrEqualTo(
                        new BigDecimal("62")
                );

        assertThat(assessment.analysisSummary())
                .contains(
                        "potrebbe rendere meno"
                )
                .contains(
                        "minutaggio in diminuzione"
                )
                .contains(
                        "fantamedia in calo"
                );
    }

    @Test
    void shouldMarkAssessmentAsInsufficientWithoutTwoSeasons() {

        TestContext context =
                createOfficialPlayer(
                        "Development Insufficient Player",
                        "development insufficient player"
                );

        createStatistics(
                context.playerId(),
                context.clubId(),
                context.latestSeasonId(),
                20,
                15,
                1400,
                "6.20",
                4,
                3,
                "3.50",
                "2.50"
        );

        service.generate("2026/2027");

        PlayerDevelopmentAssessmentResponse assessment =
                service.findByPlayer(
                        context.playerId(),
                        "2026/2027"
                );

        assertThat(assessment.trend())
                .isEqualTo("INSUFFICIENT_DATA");

        assertThat(assessment.confidenceScore())
                .isEqualByComparingTo(
                        BigDecimal.ZERO
                );

        assertThat(assessment.analysisSummary())
                .contains(
                        "Dati storici insufficienti"
                );
    }

    private TestContext createOfficialPlayer(
            String displayName,
            String normalizedName
    ) {
        Long targetSeasonId =
                findSeasonId("2026/2027");

        Long olderSeasonId =
                findSeasonId("2024/2025");

        Long latestSeasonId =
                findSeasonId("2025/2026");

        Long clubId =
                jdbcClient.sql("""
                        SELECT id
                        FROM clubs
                        ORDER BY id
                        LIMIT 1
                        """)
                        .query(Long.class)
                        .single();

        Long playerId =
                jdbcClient.sql("""
                        INSERT INTO players (
                            last_name,
                            display_name,
                            normalized_name,
                            active
                        )
                        VALUES (
                            :displayName,
                            :displayName,
                            :normalizedName,
                            TRUE
                        )
                        RETURNING id
                        """)
                        .param(
                                "displayName",
                                displayName
                        )
                        .param(
                                "normalizedName",
                                normalizedName
                        )
                        .query(Long.class)
                        .single();

        jdbcClient.sql("""
                INSERT INTO player_registrations (
                    player_id,
                    club_id,
                    season_id,
                    registration_status
                )
                VALUES (
                    :playerId,
                    :clubId,
                    :seasonId,
                    'ACTIVE'
                )
                """)
                .param("playerId", playerId)
                .param("clubId", clubId)
                .param("seasonId", targetSeasonId)
                .update();

        jdbcClient.sql("""
                INSERT INTO player_reconciliation_results (
                    season_id,
                    player_id,
                    normalized_name,
                    normalized_club_name,
                    sources_count
                )
                VALUES (
                    :seasonId,
                    :playerId,
                    :normalizedName,
                    'development test club',
                    3
                )
                """)
                .param("seasonId", targetSeasonId)
                .param("playerId", playerId)
                .param(
                        "normalizedName",
                        normalizedName
                )
                .update();

        return new TestContext(
                playerId,
                clubId,
                olderSeasonId,
                latestSeasonId
        );
    }

    private void createStatistics(
            Long playerId,
            Long clubId,
            Long seasonId,
            int appearances,
            int starts,
            int minutesPlayed,
            String fantasyAverage,
            int goals,
            int assists,
            String expectedGoals,
            String expectedAssists
    ) {
        jdbcClient.sql("""
                INSERT INTO player_season_statistics (
                    player_id,
                    club_id,
                    season_id,
                    source,
                    appearances,
                    starts,
                    minutes_played,
                    fantasy_average,
                    goals,
                    assists,
                    expected_goals,
                    expected_assists
                )
                VALUES (
                    :playerId,
                    :clubId,
                    :seasonId,
                    'FANTACALCIO',
                    :appearances,
                    :starts,
                    :minutesPlayed,
                    :fantasyAverage,
                    :goals,
                    :assists,
                    :expectedGoals,
                    :expectedAssists
                )
                """)
                .param("playerId", playerId)
                .param("clubId", clubId)
                .param("seasonId", seasonId)
                .param("appearances", appearances)
                .param("starts", starts)
                .param(
                        "minutesPlayed",
                        minutesPlayed
                )
                .param(
                        "fantasyAverage",
                        new BigDecimal(fantasyAverage)
                )
                .param("goals", goals)
                .param("assists", assists)
                .param(
                        "expectedGoals",
                        new BigDecimal(expectedGoals)
                )
                .param(
                        "expectedAssists",
                        new BigDecimal(expectedAssists)
                )
                .update();
    }

    private Long findSeasonId(String seasonCode) {
        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = :seasonCode
                """)
                .param("seasonCode", seasonCode)
                .query(Long.class)
                .single();
    }

    private record TestContext(
            Long playerId,
            Long clubId,
            Long olderSeasonId,
            Long latestSeasonId
    ) {
    }
}