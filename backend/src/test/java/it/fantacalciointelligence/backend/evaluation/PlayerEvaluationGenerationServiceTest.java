package it.fantacalciointelligence.backend.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlayerEvaluationGenerationServiceTest {

    @Autowired
    private PlayerEvaluationGenerationService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldGenerateDifferentEvaluationsFromMarketValues() {

        Long seasonId = findSeasonId();
        Long clubId = findClubId();
        addMembership(seasonId, clubId);

        Long premiumPlayerId = createPlayer(
                "Valutazione Test Premium",
                "valutazione test premium"
        );

        Long budgetPlayerId = createPlayer(
                "Valutazione Test Budget",
                "valutazione test budget"
        );

        createRegistration(
                premiumPlayerId,
                clubId,
                seasonId
        );

        createRegistration(
                budgetPlayerId,
                clubId,
                seasonId
        );

        createRole(
                premiumPlayerId,
                seasonId,
                "A"
        );

        createRole(
                budgetPlayerId,
                seasonId,
                "A"
        );

        createReconciliation(
                premiumPlayerId,
                seasonId,
                "valutazione test premium",
                "club test",
                2
        );

        createReconciliation(
                budgetPlayerId,
                seasonId,
                "valutazione test budget",
                "club test",
                2
        );

        createMarketValue(
                premiumPlayerId,
                seasonId,
                new BigDecimal("45"),
                new BigDecimal("320")
        );

        createMarketValue(
                budgetPlayerId,
                seasonId,
                new BigDecimal("5"),
                new BigDecimal("20")
        );

        PlayerEvaluationGenerationResponse response =
                service.generate("2024/2025");

        assertThat(response.status())
                .isEqualTo("COMPLETED");

        assertThat(response.generatedEvaluations())
                .isEqualTo(response.eligiblePlayers());

        EvaluationScores premium =
                findEvaluation(premiumPlayerId);

        EvaluationScores budget =
                findEvaluation(budgetPlayerId);

        assertThat(premium.valueScore())
                .isGreaterThan(budget.valueScore());

        assertThat(premium.reliabilityScore())
                .isGreaterThan(
                        budget.reliabilityScore()
                );

        assertThat(premium.riskScore())
                .isLessThan(budget.riskScore());

        assertThat(premium.projectedGoals())
                .isGreaterThan(
                        budget.projectedGoals()
                );

        assertThat(premium.summary())
                .contains(
                        "Valutazione automatica iniziale"
                );
    }

    private Long findSeasonId() {
        return jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = '2024/2025'
                """)
                .query(Long.class)
                .single();
    }

    private Long findClubId() {
        return jdbcClient.sql("""
                SELECT id
                FROM clubs
                ORDER BY id
                LIMIT 1
                """)
                .query(Long.class)
                .single();
    }

    private void addMembership(Long seasonId, Long clubId) {
        jdbcClient.sql("""
                INSERT INTO season_clubs(season_id,club_id) VALUES(:seasonId,:clubId)
                ON CONFLICT DO NOTHING
                """).param("seasonId",seasonId).param("clubId",clubId).update();
    }

    private Long createPlayer(
            String displayName,
            String normalizedName
    ) {
        return jdbcClient.sql("""
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
                .param("displayName", displayName)
                .param("normalizedName", normalizedName)
                .query(Long.class)
                .single();
    }

    private void createRegistration(
            Long playerId,
            Long clubId,
            Long seasonId
    ) {
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
                .param("seasonId", seasonId)
                .update();
    }

    private void createRole(
            Long playerId,
            Long seasonId,
            String role
    ) {
        jdbcClient.sql("""
                INSERT INTO player_roles (
                    player_id,
                    season_id,
                    source,
                    fantasy_role,
                    primary_role
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    'FANTACALCIO',
                    :role,
                    TRUE
                )
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("role", role)
                .update();
    }

    private void createReconciliation(
            Long playerId,
            Long seasonId,
            String normalizedName,
            String normalizedClubName,
            int sourcesCount
    ) {
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
                    :normalizedClubName,
                    :sourcesCount
                )
                """)
                .param("seasonId", seasonId)
                .param("playerId", playerId)
                .param("normalizedName", normalizedName)
                .param(
                        "normalizedClubName",
                        normalizedClubName
                )
                .param("sourcesCount", sourcesCount)
                .update();
    }

    private void createMarketValue(
            Long playerId,
            Long seasonId,
            BigDecimal quotation,
            BigDecimal fantasyMarketValue
    ) {
        jdbcClient.sql("""
                INSERT INTO player_market_values (
                    player_id,
                    season_id,
                    source,
                    quotation,
                    fantasy_market_value
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    'FANTACALCIO',
                    :quotation,
                    :fantasyMarketValue
                )
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("quotation", quotation)
                .param(
                        "fantasyMarketValue",
                        fantasyMarketValue
                )
                .update();
    }

    private EvaluationScores findEvaluation(
            Long playerId
    ) {
        return jdbcClient.sql("""
                SELECT
                    reliability_score,
                    risk_score,
                    value_score,
                    projected_goals,
                    evaluation_summary
                FROM player_evaluations
                WHERE player_id = :playerId
                """)
                .param("playerId", playerId)
                .query((resultSet, rowNumber) ->
                        new EvaluationScores(
                                resultSet.getBigDecimal(
                                        "reliability_score"
                                ),
                                resultSet.getBigDecimal(
                                        "risk_score"
                                ),
                                resultSet.getBigDecimal(
                                        "value_score"
                                ),
                                resultSet.getInt(
                                        "projected_goals"
                                ),
                                resultSet.getString(
                                        "evaluation_summary"
                                )
                        )
                )
                .single();
    }

    private record EvaluationScores(
            BigDecimal reliabilityScore,
            BigDecimal riskScore,
            BigDecimal valueScore,
            int projectedGoals,
            String summary
    ) {
    }
}
