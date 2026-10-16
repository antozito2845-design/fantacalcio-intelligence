package it.fantacalciointelligence.backend.fixture.strength;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ClubStrengthGenerationServiceTest {

    @Autowired
    private ClubStrengthGenerationService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldGenerateDifferentRatingsFromPlayerQuality() {

        Long seasonId = findSeasonId();

        List<Long> clubIds = findTwoClubIds();

        clubIds.forEach(clubId -> jdbcClient.sql("""
                INSERT INTO season_clubs(season_id,club_id) VALUES(:seasonId,:clubId)
                ON CONFLICT DO NOTHING
                """).param("seasonId",seasonId).param("clubId",clubId).update());

        Long strongClubId = clubIds.get(0);
        Long weakClubId = clubIds.get(1);

        createEligiblePlayer(
                seasonId,
                strongClubId,
                "Rating Test Strong Attacker",
                "rating test strong attacker",
                "A",
                new BigDecimal("95"),
                new BigDecimal("96"),
                new BigDecimal("10"),
                new BigDecimal("94"),
                16,
                8,
                32
        );

        createEligiblePlayer(
                seasonId,
                strongClubId,
                "Rating Test Strong Goalkeeper",
                "rating test strong goalkeeper",
                "P",
                new BigDecimal("94"),
                new BigDecimal("90"),
                new BigDecimal("12"),
                new BigDecimal("88"),
                0,
                0,
                34
        );

        createEligiblePlayer(
                seasonId,
                weakClubId,
                "Rating Test Weak Attacker",
                "rating test weak attacker",
                "A",
                new BigDecimal("25"),
                new BigDecimal("28"),
                new BigDecimal("80"),
                new BigDecimal("22"),
                1,
                1,
                8
        );

        createEligiblePlayer(
                seasonId,
                weakClubId,
                "Rating Test Weak Goalkeeper",
                "rating test weak goalkeeper",
                "P",
                new BigDecimal("30"),
                new BigDecimal("24"),
                new BigDecimal("78"),
                new BigDecimal("25"),
                0,
                0,
                7
        );

        ClubStrengthGenerationResponse response =
                service.generate("2024/2025");

        assertThat(response.status())
                .isEqualTo("COMPLETED");

        assertThat(response.eligiblePlayers())
                .isGreaterThanOrEqualTo(4);

        assertThat(response.eligibleClubs())
                .isGreaterThanOrEqualTo(2);

        assertThat(response.generatedRatings())
                .isEqualTo(countSeasonClubs(seasonId));

        ClubRating strongRating =
                findRating(strongClubId, seasonId);

        ClubRating weakRating =
                findRating(weakClubId, seasonId);

        assertThat(strongRating.overall())
                .isGreaterThan(weakRating.overall());

        assertThat(strongRating.attack())
                .isGreaterThan(weakRating.attack());

        assertThat(strongRating.defense())
                .isGreaterThan(weakRating.defense());

        assertThat(strongRating.home())
                .isGreaterThan(strongRating.away());
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

    private List<Long> findTwoClubIds() {
        return jdbcClient.sql("""
                SELECT id
                FROM clubs
                WHERE active=TRUE
                ORDER BY id
                LIMIT 2
                """)
                .query(Long.class)
                .list();
    }

    private int countSeasonClubs(Long seasonId) {
        return jdbcClient.sql("""
                SELECT COUNT(*)
                FROM season_clubs
                WHERE season_id = :seasonId
                """)
                .param("seasonId", seasonId)
                .query(Integer.class)
                .single();
    }

    private void createEligiblePlayer(
            Long seasonId,
            Long clubId,
            String displayName,
            String normalizedName,
            String role,
            BigDecimal reliability,
            BigDecimal potential,
            BigDecimal risk,
            BigDecimal value,
            int goals,
            int assists,
            int starts
    ) {
        Long playerId = jdbcClient.sql("""
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
                    'rating test club',
                    3
                )
                """)
                .param("seasonId", seasonId)
                .param("playerId", playerId)
                .param("normalizedName", normalizedName)
                .update();

        jdbcClient.sql("""
                INSERT INTO player_evaluations (
                    player_id,
                    season_id,
                    projected_fantasy_average,
                    projected_appearances,
                    projected_starts,
                    projected_goals,
                    projected_assists,
                    reliability_score,
                    potential_score,
                    risk_score,
                    value_score,
                    evaluation_summary
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    6.00,
                    36,
                    :starts,
                    :goals,
                    :assists,
                    :reliability,
                    :potential,
                    :risk,
                    :value,
                    'Test generazione rating squadra'
                )
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("starts", starts)
                .param("goals", goals)
                .param("assists", assists)
                .param("reliability", reliability)
                .param("potential", potential)
                .param("risk", risk)
                .param("value", value)
                .update();
    }

    private ClubRating findRating(
            Long clubId,
            Long seasonId
    ) {
        return jdbcClient.sql("""
                SELECT
                    overall_rating,
                    attacking_rating,
                    defensive_rating,
                    home_rating,
                    away_rating
                FROM club_strength_ratings
                WHERE club_id = :clubId
                  AND season_id = :seasonId
                """)
                .param("clubId", clubId)
                .param("seasonId", seasonId)
                .query((resultSet, rowNumber) ->
                        new ClubRating(
                                resultSet.getInt(
                                        "overall_rating"
                                ),
                                resultSet.getInt(
                                        "attacking_rating"
                                ),
                                resultSet.getInt(
                                        "defensive_rating"
                                ),
                                resultSet.getInt(
                                        "home_rating"
                                ),
                                resultSet.getInt(
                                        "away_rating"
                                )
                        )
                )
                .single();
    }

    private record ClubRating(
            int overall,
            int attack,
            int defense,
            int home,
            int away
    ) {
    }
}
