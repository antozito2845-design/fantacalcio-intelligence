package it.fantacalciointelligence.backend.player;

import java.math.BigDecimal;

public record PlayerEvaluationResponse(
        BigDecimal projectedFantasyAverage,
        Integer projectedAppearances,
        Integer projectedStarts,
        Integer projectedGoals,
        Integer projectedAssists,
        BigDecimal reliabilityScore,
        BigDecimal potentialScore,
        BigDecimal riskScore,
        BigDecimal valueScore,
        String summary
) {
}