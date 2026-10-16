package it.fantacalciointelligence.backend.lineup;

import java.math.BigDecimal;

public record LineupPlayerResponse(
        Long playerId,
        String playerName,
        String club,
        String role,
        String opponent,
        boolean home,
        BigDecimal fixtureDifficulty,
        BigDecimal selectionScore,
        String availabilityStatus,
        String reason
) {
}