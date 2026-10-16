package it.fantacalciointelligence.backend.intelligence;

import java.math.BigDecimal;

public record PlayerIntelligenceProfileResponse(
        Long playerId,
        String playerName,
        String season,
        String fantasyRole,
        String modelVersion,
        BigDecimal dataCompleteness,
        String confidence,
        ProfileScore starterScore,
        ProfileScore reliabilityScore,
        ProfileScore bonusPotentialScore,
        ProfileScore malusRiskScore,
        ProfileScore consistencyScore,
        ProfileScore trendScore
) {
}
