package it.fantacalciointelligence.backend.strategic;

import java.sql.Types;
import java.util.Locale;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StrategicDataObservationService {
    private final JdbcClient jdbcClient;

    public StrategicDataObservationService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public Long upsert(StrategicObservationUpsert observation) {
        if (observation == null || observation.playerId() == null || observation.seasonId() == null) {
            throw new IllegalArgumentException("Player and season are required");
        }
        if (observation.source() == null || observation.source().isBlank()) {
            throw new IllegalArgumentException("Source is required");
        }

        String conflictClause = observation.sourceRecordId() == null
                ? "ON CONFLICT (player_id, season_id, source) WHERE source_record_id IS NULL\n"
                : "ON CONFLICT (player_id, season_id, source, source_record_id)\n";

        return jdbcClient.sql("""
                INSERT INTO player_strategic_data_observations (
                    player_id, season_id, source, source_record_id, observed_at,
                    evidence_status, confidence_score, real_position_raw,
                    real_position_normalized, penalty_hierarchy, shots, shots_on_target,
                    expected_goals, expected_assists, aerial_duels, aerial_duels_won,
                    headed_shots, headed_goals, headed_expected_goals, appearances,
                    starts, minutes_played, starter_probability, evidence
                ) VALUES (
                    :playerId, :seasonId, :source, :sourceRecordId, :observedAt,
                    :evidenceStatus, :confidenceScore, :realPositionRaw,
                    :realPositionNormalized, :penaltyHierarchy, :shots, :shotsOnTarget,
                    :expectedGoals, :expectedAssists, :aerialDuels, :aerialDuelsWon,
                    :headedShots, :headedGoals, :headedExpectedGoals, :appearances,
                    :starts, :minutesPlayed, :starterProbability, CAST(:evidence AS JSONB)
                )
                """ + conflictClause + """
                DO UPDATE SET
                    observed_at = EXCLUDED.observed_at,
                    imported_at = CURRENT_TIMESTAMP,
                    evidence_status = EXCLUDED.evidence_status,
                    confidence_score = EXCLUDED.confidence_score,
                    real_position_raw = EXCLUDED.real_position_raw,
                    real_position_normalized = EXCLUDED.real_position_normalized,
                    penalty_hierarchy = EXCLUDED.penalty_hierarchy,
                    shots = EXCLUDED.shots,
                    shots_on_target = EXCLUDED.shots_on_target,
                    expected_goals = EXCLUDED.expected_goals,
                    expected_assists = EXCLUDED.expected_assists,
                    aerial_duels = EXCLUDED.aerial_duels,
                    aerial_duels_won = EXCLUDED.aerial_duels_won,
                    headed_shots = EXCLUDED.headed_shots,
                    headed_goals = EXCLUDED.headed_goals,
                    headed_expected_goals = EXCLUDED.headed_expected_goals,
                    appearances = EXCLUDED.appearances,
                    starts = EXCLUDED.starts,
                    minutes_played = EXCLUDED.minutes_played,
                    starter_probability = EXCLUDED.starter_probability,
                    evidence = EXCLUDED.evidence,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """)
                .param("playerId", observation.playerId())
                .param("seasonId", observation.seasonId())
                .param("source", observation.source().trim().toUpperCase(Locale.ROOT))
                .param("sourceRecordId", observation.sourceRecordId(), Types.VARCHAR)
                .param("observedAt", observation.observedAt(), Types.TIMESTAMP_WITH_TIMEZONE)
                .param("evidenceStatus", observation.evidenceStatus() == null ? "NOT_AVAILABLE" : observation.evidenceStatus())
                .param("confidenceScore", observation.confidenceScore(), Types.NUMERIC)
                .param("realPositionRaw", observation.realPositionRaw(), Types.VARCHAR)
                .param("realPositionNormalized", observation.realPositionNormalized() == null ? null : observation.realPositionNormalized().name(), Types.VARCHAR)
                .param("penaltyHierarchy", observation.penaltyHierarchy(), Types.VARCHAR)
                .param("shots", observation.shots(), Types.INTEGER)
                .param("shotsOnTarget", observation.shotsOnTarget(), Types.INTEGER)
                .param("expectedGoals", observation.expectedGoals(), Types.NUMERIC)
                .param("expectedAssists", observation.expectedAssists(), Types.NUMERIC)
                .param("aerialDuels", observation.aerialDuels(), Types.INTEGER)
                .param("aerialDuelsWon", observation.aerialDuelsWon(), Types.INTEGER)
                .param("headedShots", observation.headedShots(), Types.INTEGER)
                .param("headedGoals", observation.headedGoals(), Types.INTEGER)
                .param("headedExpectedGoals", observation.headedExpectedGoals(), Types.NUMERIC)
                .param("appearances", observation.appearances(), Types.INTEGER)
                .param("starts", observation.starts(), Types.INTEGER)
                .param("minutesPlayed", observation.minutesPlayed(), Types.INTEGER)
                .param("starterProbability", observation.starterProbability(), Types.NUMERIC)
                .param("evidence", observation.evidenceJson() == null ? "{}" : observation.evidenceJson())
                .query(Long.class)
                .single();
    }
}
