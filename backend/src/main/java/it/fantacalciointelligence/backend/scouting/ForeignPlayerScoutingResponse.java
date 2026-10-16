package it.fantacalciointelligence.backend.scouting;

import java.math.BigDecimal;
import java.time.Instant;

public record ForeignPlayerScoutingResponse(
        Long playerId,
        String playerName,
        String currentClub,
        String fantasyRole,
        String season,
        String previousClub,
        String previousLeague,
        String previousCountryCode,
        String transferType,
        BigDecimal adaptationScore,
        BigDecimal potentialScore,
        BigDecimal riskScore,
        BigDecimal confidenceScore,
        String recommendation,
        String scoutingSummary,
        Instant updatedAt
) {
}