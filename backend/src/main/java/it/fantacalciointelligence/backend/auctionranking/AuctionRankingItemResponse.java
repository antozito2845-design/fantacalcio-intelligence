package it.fantacalciointelligence.backend.auctionranking;

import java.math.BigDecimal;
import java.util.List;

public record AuctionRankingItemResponse(
        int position,
        Long playerId,
        String playerName,
        String club,
        String fantasyRole,

        BigDecimal intelligenceScore,

        int minimumPrice,
        int recommendedPrice,
        int maximumPrice,
        BigDecimal budgetPercentage,

        BigDecimal reliabilityScore,
        BigDecimal potentialScore,
        BigDecimal riskScore,
        BigDecimal starterProbability,

        String availabilityStatus,
        Integer availabilityMatchday,

        List<String> strengths,
        List<String> risks,

        String finalRecommendation
) {
}