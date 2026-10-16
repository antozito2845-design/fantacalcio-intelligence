package it.fantacalciointelligence.backend.pairing;

import java.math.BigDecimal;

public record PairingPlayerResponse(
        Long playerId,
        String playerName,
        String club,
        String role,
        int recommendedPrice,
        BigDecimal evaluationScore,
        BigDecimal averageFixtureDifficulty
) {
}