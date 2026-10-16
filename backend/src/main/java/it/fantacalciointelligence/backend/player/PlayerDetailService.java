package it.fantacalciointelligence.backend.player;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerDetailService {

    private final JdbcClient jdbcClient;

    public PlayerDetailService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public PlayerDetailResponse findById(
            Long playerId,
            String seasonCode
    ) {
        Map<String, Object> player = jdbcClient.sql("""
                SELECT
                    p.id,
                    p.display_name,
                    c.name AS club_name,
                    c.slug AS club_slug,
                    s.id AS season_id
                FROM players p
                JOIN player_registrations pr
                    ON pr.player_id = p.id
                JOIN seasons s
                    ON s.id = pr.season_id
                JOIN clubs c
                    ON c.id = pr.club_id
                WHERE p.id = :playerId
                  AND s.code = :seasonCode
                """)
                .param("playerId", playerId)
                .param("seasonCode", seasonCode)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Giocatore non trovato"
                        )
                );

        Long seasonId = ((Number) player.get("season_id"))
                .longValue();

        Map<String, String> roles = new LinkedHashMap<>();

        jdbcClient.sql("""
                SELECT source, fantasy_role
                FROM player_roles
                WHERE player_id = :playerId
                  AND season_id = :seasonId
                ORDER BY source
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .query()
                .listOfRows()
                .forEach(row ->
                        roles.put(
                                row.get("source").toString(),
                                row.get("fantasy_role").toString()
                        )
                );

        PlayerTacticalProfileResponse tacticalProfile =
                jdbcClient.sql("""
                        SELECT
                            real_position,
                            secondary_position,
                            tactical_role,
                            attacking_involvement,
                            defensive_involvement,
                            set_piece_taker,
                            penalty_taker,
                            corner_taker,
                            free_kick_taker,
                            projected_starter,
                            starter_probability,
                            competition_level,
                            tactical_notes
                        FROM player_tactical_profiles
                        WHERE player_id = :playerId
                          AND season_id = :seasonId
                        """)
                        .param("playerId", playerId)
                        .param("seasonId", seasonId)
                        .query((rs, rowNum) ->
                                new PlayerTacticalProfileResponse(
                                        rs.getString("real_position"),
                                        rs.getString("secondary_position"),
                                        rs.getString("tactical_role"),
                                        rs.getBigDecimal("attacking_involvement"),
                                        rs.getBigDecimal("defensive_involvement"),
                                        rs.getBoolean("set_piece_taker"),
                                        rs.getBoolean("penalty_taker"),
                                        rs.getBoolean("corner_taker"),
                                        rs.getBoolean("free_kick_taker"),
                                        rs.getBoolean("projected_starter"),
                                        rs.getBigDecimal("starter_probability"),
                                        rs.getString("competition_level"),
                                        rs.getString("tactical_notes")
                                )
                        )
                        .optional()
                        .orElse(null);

        ClubTacticalResponse clubTactics =
                jdbcClient.sql("""
                        SELECT
                            coach.full_name,
                            tactics.primary_formation,
                            tactics.secondary_formation,
                            tactics.playing_style,
                            tactics.defensive_line,
                            tactics.pressing_level
                        FROM player_registrations pr
                        JOIN club_season_tactics tactics
                            ON tactics.club_id = pr.club_id
                            AND tactics.season_id = pr.season_id
                        LEFT JOIN coaches coach
                            ON coach.id = tactics.coach_id
                        WHERE pr.player_id = :playerId
                          AND pr.season_id = :seasonId
                        """)
                        .param("playerId", playerId)
                        .param("seasonId", seasonId)
                        .query((rs, rowNum) ->
                                new ClubTacticalResponse(
                                        rs.getString("full_name"),
                                        rs.getString("primary_formation"),
                                        rs.getString("secondary_formation"),
                                        rs.getString("playing_style"),
                                        rs.getString("defensive_line"),
                                        rs.getString("pressing_level")
                                )
                        )
                        .optional()
                        .orElse(null);

        PlayerEvaluationResponse evaluation =
                jdbcClient.sql("""
                        SELECT
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
                        FROM player_evaluations
                        WHERE player_id = :playerId
                          AND season_id = :seasonId
                        """)
                        .param("playerId", playerId)
                        .param("seasonId", seasonId)
                        .query((rs, rowNum) ->
                                new PlayerEvaluationResponse(
                                        rs.getBigDecimal("projected_fantasy_average"),
                                        (Integer) rs.getObject("projected_appearances"),
                                        (Integer) rs.getObject("projected_starts"),
                                        (Integer) rs.getObject("projected_goals"),
                                        (Integer) rs.getObject("projected_assists"),
                                        rs.getBigDecimal("reliability_score"),
                                        rs.getBigDecimal("potential_score"),
                                        rs.getBigDecimal("risk_score"),
                                        rs.getBigDecimal("value_score"),
                                        rs.getString("evaluation_summary")
                                )
                        )
                        .optional()
                        .orElse(null);

        List<PlayerSeasonStatisticsResponse> statistics =
                jdbcClient.sql("""
                        SELECT
                            s.code,
                            stats.source,
                            stats.source_external_id,
                            club.name AS club_name,
                            (SELECT STRING_AGG(history.club_name, '|' ORDER BY history.source_order)
                             FROM player_season_statistics_clubs history
                             WHERE history.statistics_id = stats.id) AS historical_clubs,
                            stats.appearances,
                            stats.starts,
                            stats.minutes_played,
                            stats.average_rating,
                            stats.fantasy_average,
                            stats.goals,
                            stats.assists,
                            stats.penalties_taken,
                            stats.penalties_scored,
                            stats.penalties_missed,
                            stats.yellow_cards,
                            stats.red_cards,
                            stats.clean_sheets,
                            stats.goals_conceded,
                            stats.saves,
                            stats.penalties_saved,
                            stats.expected_goals,
                            stats.expected_assists,
                            CASE WHEN stats.minutes_played > 0 AND stats.goals IS NOT NULL
                                THEN ROUND(stats.goals * 90.0 / stats.minutes_played, 2) END AS goals_per_90,
                            CASE WHEN stats.minutes_played > 0 AND stats.assists IS NOT NULL
                                THEN ROUND(stats.assists * 90.0 / stats.minutes_played, 2) END AS assists_per_90,
                            CASE WHEN stats.appearances > 0 AND stats.minutes_played IS NOT NULL
                                THEN ROUND(stats.minutes_played * 1.0 / stats.appearances, 2) END AS minutes_per_appearance,
                            CASE WHEN stats.appearances > 0 AND stats.starts IS NOT NULL
                                THEN ROUND(stats.starts * 100.0 / stats.appearances, 2) END AS start_percentage,
                            stats.imported_at,
                            stats.source_updated_at,
                            stats.import_batch_id
                        FROM player_season_statistics stats
                        JOIN seasons s
                            ON s.id = stats.season_id
                        LEFT JOIN clubs club
                            ON club.id = stats.club_id
                        WHERE stats.player_id = :playerId
                        ORDER BY s.start_year DESC, stats.source
                        LIMIT 10
                        """)
                        .param("playerId", playerId)
                        .query((rs, rowNum) ->
                                new PlayerSeasonStatisticsResponse(
                                        rs.getString("code"),
                                        rs.getString("source"),
                                        rs.getString("source_external_id"),
                                        rs.getString("club_name"),
                                        splitHistoricalClubs(rs.getString("historical_clubs")),
                                        rs.getObject("appearances", Integer.class),
                                        rs.getObject("starts", Integer.class),
                                        rs.getObject("minutes_played", Integer.class),
                                        rs.getBigDecimal("average_rating"),
                                        rs.getBigDecimal("fantasy_average"),
                                        rs.getObject("goals", Integer.class),
                                        rs.getObject("assists", Integer.class),
                                        rs.getObject("penalties_taken", Integer.class),
                                        rs.getObject("penalties_scored", Integer.class),
                                        rs.getObject("penalties_missed", Integer.class),
                                        rs.getObject("yellow_cards", Integer.class),
                                        rs.getObject("red_cards", Integer.class),
                                        rs.getObject("clean_sheets", Integer.class),
                                        rs.getObject("goals_conceded", Integer.class),
                                        rs.getObject("saves", Integer.class),
                                        rs.getObject("penalties_saved", Integer.class),
                                        rs.getBigDecimal("expected_goals"),
                                        rs.getBigDecimal("expected_assists"),
                                        rs.getBigDecimal("goals_per_90"),
                                        rs.getBigDecimal("assists_per_90"),
                                        rs.getBigDecimal("minutes_per_appearance"),
                                        rs.getBigDecimal("start_percentage"),
                                        rs.getObject("imported_at", java.time.OffsetDateTime.class),
                                        rs.getObject("source_updated_at", java.time.OffsetDateTime.class),
                                        rs.getObject("import_batch_id", Long.class)
                                )
                        )
                        .list();

        return new PlayerDetailResponse(
                ((Number) player.get("id")).longValue(),
                player.get("display_name").toString(),
                player.get("club_name").toString(),
                player.get("club_slug").toString(),
                roles,
                tacticalProfile,
                clubTactics,
                evaluation,
                statistics
        );
    }

    private List<String> splitHistoricalClubs(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : List.of(value.split("\\|"));
    }
}
