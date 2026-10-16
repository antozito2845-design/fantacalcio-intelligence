package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.util.List;

public record BonusOpportunityInput(
        String penaltyHierarchy, String directFreeKickHierarchy,
        String indirectFreeKickHierarchy, String cornerHierarchy,
        Boolean sharedSetPieces, BigDecimal expectedGoalsPer90,
        BigDecimal expectedAssistsPer90, BigDecimal shotsPer90,
        Integer touchesInBox, Integer keyPasses, Integer minutes,
        BigDecimal starterProbability, BigDecimal evidenceConfidence,
        List<String> sources, boolean sourceConflict
) {}
