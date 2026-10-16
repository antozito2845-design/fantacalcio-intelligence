package it.fantacalciointelligence.backend.auctionstrategy;

import java.math.BigDecimal;
import java.util.List;

public record AuctionRoleStrategyResponse(
        String role,
        int requiredSlots,

        BigDecimal budgetPercentage,
        int allocatedBudget,
        int plannedSpending,
        int remainingRoleBudget,

        List<AuctionStrategyPlayerResponse> primaryTargets,
        List<AuctionStrategyPlayerResponse> valueAlternatives,
        List<AuctionStrategyPlayerResponse> highPotentialBets,
        List<AuctionStrategyPlayerResponse> playersToAvoid,

        List<String> warnings
) {
}