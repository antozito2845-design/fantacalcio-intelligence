package it.fantacalciointelligence.backend.auctionlive;

import java.math.BigDecimal;
import java.util.List;

public record LiveAuctionTargetResponse(
        Long playerId,
        String playerName,
        String club,
        String role,

        BigDecimal intelligenceScore,
        BigDecimal reliabilityScore,
        BigDecimal potentialScore,
        BigDecimal riskScore,
        BigDecimal starterProbability,

        int minimumPrice,
        int recommendedPrice,
        int originalMaximumPrice,
        int liveMaximumBid,

        String liveCategory,
        String finalRecommendation,

        List<String> strengths,
        List<String> risks
) {
}