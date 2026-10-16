package it.fantacalciointelligence.backend.squadanalysis;

import java.math.BigDecimal;

public record SquadPairingResponse(
        int position,
        String role,

        Long firstPlayerId,
        String firstPlayerName,
        String firstClub,

        Long secondPlayerId,
        String secondPlayerName,
        String secondClub,

        int combinedPurchasePrice,
        BigDecimal complementarityScore,

        String recommendation,
        String explanation
) {
}