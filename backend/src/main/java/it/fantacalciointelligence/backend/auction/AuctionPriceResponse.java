package it.fantacalciointelligence.backend.auction;

import java.math.BigDecimal;

public record AuctionPriceResponse(
        Long playerId,
        String playerName,
        String club,
        String role,
        int leagueTeams,
        int totalBudget,
        boolean defenseModifier,
        int minimumPrice,
        int recommendedPrice,
        int maximumPrice,
        BigDecimal budgetPercentage,
        BigDecimal calculationScore,
        String recommendation,
        String explanation
) {
}