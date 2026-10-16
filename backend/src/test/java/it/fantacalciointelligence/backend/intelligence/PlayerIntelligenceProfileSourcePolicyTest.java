package it.fantacalciointelligence.backend.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.intelligence.PlayerIntelligenceProfileCalculator.SeasonData;

@SpringBootTest
@Transactional
class PlayerIntelligenceProfileSourcePolicyTest {
    @Autowired private JdbcClient jdbcClient;
    @Autowired private PlayerIntelligenceProfileService service;

    @Test
    void fbrefIsPrimaryForObjectivesButNeverForRatingsOrFantasyRole() {
        Long playerId = jdbcClient.sql("""
                INSERT INTO players (last_name, display_name, normalized_name, active)
                VALUES ('Policy', 'FBref Policy Test', 'fbref policy test', TRUE) RETURNING id
                """).query(Long.class).single();
        Long seasonId = jdbcClient.sql("SELECT id FROM seasons WHERE code = '2025/2026'")
                .query(Long.class).single();
        Long clubId = jdbcClient.sql("SELECT id FROM clubs ORDER BY id LIMIT 1").query(Long.class).single();
        jdbcClient.sql("""
                INSERT INTO player_roles (player_id, season_id, source, fantasy_role)
                VALUES (:playerId, :seasonId, 'FANTACALCIO', 'D')
                """).param("playerId", playerId).param("seasonId", seasonId).update();
        jdbcClient.sql("""
                INSERT INTO player_season_statistics (
                    player_id, club_id, season_id, source, appearances, starts, minutes_played,
                    goals, assists, yellow_cards, red_cards, penalties_missed,
                    average_rating, fantasy_average)
                VALUES
                    (:playerId, :clubId, :seasonId, 'FANTACALCIO', 20, 10, 1000, 1, 1, 2, 0, 0, 6.20, 6.55),
                    (:playerId, NULL, :seasonId, 'FBREF', 30, 25, 2400, 5, 4, 6, 1, 1, 9.99, 19.99)
                """).param("playerId", playerId).param("clubId", clubId).param("seasonId", seasonId).update();

        SeasonData data = service.loadHistory(playerId, 2025).getFirst();
        assertThat(data.appearances()).isEqualTo(30);
        assertThat(data.starts()).isEqualTo(25);
        assertThat(data.goals()).isEqualTo(5);
        assertThat(data.role()).isEqualTo("D");
        assertThat(data.averageRating()).isEqualByComparingTo(new BigDecimal("6.20"));
        assertThat(data.fantasyAverage()).isEqualByComparingTo(new BigDecimal("6.55"));
    }
}
