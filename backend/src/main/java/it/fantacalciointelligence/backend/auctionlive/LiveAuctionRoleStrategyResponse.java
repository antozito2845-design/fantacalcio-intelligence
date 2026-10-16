package it.fantacalciointelligence.backend.auctionlive;

import java.util.List;

public record LiveAuctionRoleStrategyResponse(
        String role,

        int currentPlayers,
        int maximumPlayers,
        int remainingSlots,

        int allocatedRemainingBudget,
        int maximumAffordableBid,

        boolean roleComplete,

        List<LiveAuctionTargetResponse> targets,
        List<String> warnings
) {
}