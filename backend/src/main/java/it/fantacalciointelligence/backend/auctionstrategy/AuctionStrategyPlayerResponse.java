package it.fantacalciointelligence.backend.auctionstrategy;

import java.math.BigDecimal;
import java.util.List;

public record AuctionStrategyPlayerResponse(
        Long playerId,
        String playerName,
        String club,
        String fantasyRole,
        String category,

        BigDecimal intelligenceScore,
        BigDecimal potentialScore,
        BigDecimal riskScore,
        BigDecimal starterProbability,

        int minimumPrice,
        int recommendedPrice,
        int maximumPrice,

        String finalRecommendation,
        List<String> strengths,
        List<String> risks
) {
}