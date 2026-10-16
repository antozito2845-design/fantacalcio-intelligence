package it.fantacalciointelligence.backend.strategic;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategicDataInspectionService {
    private final JdbcClient jdbcClient;

    public StrategicDataInspectionService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public List<StrategicObservationResponse> findObservations(Long playerId, String seasonCode) {
        return jdbcClient.sql("""
                SELECT o.*,
                    CASE WHEN o.minutes_played > 0 AND o.shots IS NOT NULL THEN ROUND(o.shots * 90.0 / o.minutes_played, 4) END shots_per_90,
                    CASE WHEN o.minutes_played > 0 AND o.expected_goals IS NOT NULL THEN ROUND(o.expected_goals * 90.0 / o.minutes_played, 4) END expected_goals_per_90,
                    CASE WHEN o.minutes_played > 0 AND o.expected_assists IS NOT NULL THEN ROUND(o.expected_assists * 90.0 / o.minutes_played, 4) END expected_assists_per_90,
                    CASE WHEN o.aerial_duels > 0 AND o.aerial_duels_won IS NOT NULL THEN ROUND(o.aerial_duels_won * 100.0 / o.aerial_duels, 2) END aerial_won_pct,
                    CASE WHEN o.appearances > 0 AND o.starts IS NOT NULL THEN ROUND(o.starts * 100.0 / o.appearances, 2) END start_rate,
                    CASE WHEN o.appearances > 0 AND o.minutes_played IS NOT NULL THEN ROUND(o.minutes_played * 1.0 / o.appearances, 2) END minutes_per_appearance
                FROM player_strategic_data_observations o
                JOIN seasons s ON s.id = o.season_id
                WHERE o.player_id = :playerId AND s.code = :seasonCode
                ORDER BY o.source, o.observed_at DESC NULLS LAST, o.id
                """)
                .param("playerId", playerId)
                .param("seasonCode", seasonCode)
                .query((rs, rowNum) -> new StrategicObservationResponse(
                        rs.getLong("id"), rs.getString("source"), rs.getString("source_record_id"),
                        rs.getObject("observed_at", java.time.OffsetDateTime.class),
                        rs.getObject("imported_at", java.time.OffsetDateTime.class),
                        rs.getString("evidence_status"), rs.getBigDecimal("confidence_score"),
                        rs.getString("real_position_raw"), position(rs.getString("real_position_normalized")),
                        rs.getString("penalty_hierarchy"), rs.getString("direct_free_kick_hierarchy"),
                        rs.getString("indirect_free_kick_hierarchy"), rs.getString("corner_hierarchy"),
                        rs.getObject("shots", Integer.class), rs.getObject("shots_on_target", Integer.class), rs.getBigDecimal("shots_per_90"),
                        rs.getBigDecimal("expected_goals"), rs.getBigDecimal("expected_goals_per_90"),
                        rs.getBigDecimal("expected_assists"), rs.getBigDecimal("expected_assists_per_90"),
                        rs.getObject("touches_in_box", Integer.class), rs.getObject("key_passes", Integer.class),
                        rs.getObject("shot_creating_actions", Integer.class), rs.getObject("goal_creating_actions", Integer.class),
                        rs.getObject("penalties_attempted", Integer.class), rs.getObject("penalties_scored", Integer.class),
                        rs.getObject("penalties_missed", Integer.class),
                        rs.getObject("aerial_duels", Integer.class), rs.getObject("aerial_duels_won", Integer.class), rs.getBigDecimal("aerial_won_pct"),
                        rs.getObject("headed_shots", Integer.class), rs.getObject("headed_goals", Integer.class), rs.getBigDecimal("headed_expected_goals"),
                        rs.getObject("offensive_box_touches", Integer.class), rs.getBigDecimal("set_piece_box_presence"),
                        rs.getBigDecimal("corner_target_frequency"),
                        rs.getObject("appearances", Integer.class), rs.getObject("starts", Integer.class), rs.getObject("minutes_played", Integer.class),
                        rs.getBigDecimal("start_rate"), rs.getBigDecimal("minutes_per_appearance"), rs.getBigDecimal("starter_probability"),
                        rs.getString("evidence")
                )).list();
    }

    private RealPosition position(String value) {
        return value == null ? null : RealPosition.valueOf(value);
    }
}
