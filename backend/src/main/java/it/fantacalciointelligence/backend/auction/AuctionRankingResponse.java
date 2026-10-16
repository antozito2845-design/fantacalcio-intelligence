package it.fantacalciointelligence.backend.auction;

import java.math.BigDecimal;

public record AuctionRankingResponse(
        int position,
        Long playerId,
        String playerName,
        String club,
        String role,
        int minimumPrice,
        int recommendedPrice,
        int maximumPrice,
        BigDecimal budgetPercentage,
        BigDecimal calculationScore,
        String recommendation
) {
}