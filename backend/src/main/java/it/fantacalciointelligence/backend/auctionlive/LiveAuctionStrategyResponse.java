package it.fantacalciointelligence.backend.auctionlive;

import java.util.List;

public record LiveAuctionStrategyResponse(
        Long squadId,
        String squadName,
        String season,

        int teams,
        boolean defenseModifier,

        int initialBudget,
        int spentBudget,
        int remainingBudget,

        int totalPlayers,
        int maximumPlayers,
        int remainingSlots,

        int globalMaximumBid,
        boolean squadComplete,

        String strategySummary,

        List<LiveAuctionRoleStrategyResponse> roles,
        List<String> globalWarnings
) {
}