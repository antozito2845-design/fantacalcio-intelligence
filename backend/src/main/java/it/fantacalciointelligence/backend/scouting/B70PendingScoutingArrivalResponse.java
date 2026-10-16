package it.fantacalciointelligence.backend.scouting;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record B70PendingScoutingArrivalResponse(
        Long playerId,
        String playerName,
        String club,
        String role,
        String season,
        LocalDate effectiveAt,
        Long transferClaimId,
        String transferSource,
        String transferReference,
        String scoutingStatus,
        OffsetDateTime detectedAt
) {
}