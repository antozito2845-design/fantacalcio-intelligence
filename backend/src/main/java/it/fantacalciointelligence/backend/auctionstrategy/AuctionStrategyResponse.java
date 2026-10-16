package it.fantacalciointelligence.backend.auctionstrategy;

import java.util.List;

public record AuctionStrategyResponse(
        String season,
        int totalBudget,
        int teams,
        boolean defenseModifier,

        int totalRequiredPlayers,
        int totalPlannedSpending,
        int expectedRemainingBudget,

        boolean withinBudget,
        String strategySummary,

        List<AuctionRoleStrategyResponse> roles,
        List<String> globalWarnings
) {
}