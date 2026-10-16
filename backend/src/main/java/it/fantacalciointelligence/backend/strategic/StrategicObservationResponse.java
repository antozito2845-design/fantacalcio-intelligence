package it.fantacalciointelligence.backend.strategic;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record StrategicObservationResponse(
        Long id, String source, String sourceRecordId, OffsetDateTime observedAt,
        OffsetDateTime importedAt, String evidenceStatus, BigDecimal confidenceScore,
        String realPositionRaw, RealPosition realPositionNormalized,
        String penaltyHierarchy, String directFreeKickHierarchy,
        String indirectFreeKickHierarchy, String cornerHierarchy,
        Integer shots, Integer shotsOnTarget, BigDecimal shotsPer90,
        BigDecimal expectedGoals, BigDecimal expectedGoalsPer90,
        BigDecimal expectedAssists, BigDecimal expectedAssistsPer90,
        Integer touchesInBox, Integer keyPasses, Integer shotCreatingActions,
        Integer goalCreatingActions, Integer penaltiesAttempted,
        Integer penaltiesScored, Integer penaltiesMissed,
        Integer aerialDuels, Integer aerialDuelsWon, BigDecimal aerialDuelsWonPercentage,
        Integer headedShots, Integer headedGoals, BigDecimal headedExpectedGoals,
        Integer offensiveBoxTouches, BigDecimal setPieceBoxPresence,
        BigDecimal cornerTargetFrequency,
        Integer appearances, Integer starts, Integer minutesPlayed,
        BigDecimal startRate, BigDecimal minutesPerAppearance,
        BigDecimal starterProbability, String evidence
) {}
