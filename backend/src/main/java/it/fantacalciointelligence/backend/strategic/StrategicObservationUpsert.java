package it.fantacalciointelligence.backend.strategic;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StrategicObservationUpsert(
        Long playerId,
        Long seasonId,
        String source,
        String sourceRecordId,
        OffsetDateTime observedAt,
        String evidenceStatus,
        BigDecimal confidenceScore,
        String realPositionRaw,
        RealPosition realPositionNormalized,
        String penaltyHierarchy,
        Integer shots,
        Integer shotsOnTarget,
        BigDecimal expectedGoals,
        BigDecimal expectedAssists,
        Integer aerialDuels,
        Integer aerialDuelsWon,
        Integer headedShots,
        Integer headedGoals,
        BigDecimal headedExpectedGoals,
        Integer appearances,
        Integer starts,
        Integer minutesPlayed,
        BigDecimal starterProbability,
        String evidenceJson
) {}
