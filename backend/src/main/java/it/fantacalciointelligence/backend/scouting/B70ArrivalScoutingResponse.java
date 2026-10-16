package it.fantacalciointelligence.backend.scouting;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record B70ArrivalScoutingResponse(
        Long playerId,
        String playerName,
        String club,
        String role,
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
        String eligibilityStatus,
        OffsetDateTime updatedAt
) {
}