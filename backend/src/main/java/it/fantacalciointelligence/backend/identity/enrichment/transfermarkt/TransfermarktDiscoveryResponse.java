package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.time.LocalDate;
import java.util.List;

public record TransfermarktDiscoveryResponse(
        String season,
        String club,
        int requestedPlayers,
        int matchedVerified,
        int reviewRequired,
        int notFound,
        int failed,
        boolean persisted,
        List<PlayerDiscovery> players
) {

    public record PlayerDiscovery(
            Long playerId,
            String playerName,
            String club,
            String status,
            int score,
            String transfermarktPlayerId,
            String transfermarktName,
            String transfermarktUrl,
            String transfermarktCurrentClub,
            LocalDate birthDate,
            String nationality,
            Integer heightCm,
            String preferredFoot,
            String reason
    ) {
    }
}