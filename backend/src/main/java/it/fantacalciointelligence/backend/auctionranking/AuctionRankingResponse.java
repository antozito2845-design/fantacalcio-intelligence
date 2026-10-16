package it.fantacalciointelligence.backend.auctionranking;

import java.util.List;

public record AuctionRankingResponse(
        String season,
        int budget,
        int teams,
        boolean defenseModifier,

        String role,
        String club,
        Integer maximumPrice,
        String recommendation,
        boolean excludeUnavailable,

        int totalPlayers,
        List<AuctionRankingItemResponse> players
) {
}