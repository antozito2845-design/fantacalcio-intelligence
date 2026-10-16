package it.fantacalciointelligence.backend.scouting;

import java.time.OffsetDateTime;

public record B70NewListoneArrivalResponse(
        Long playerId,
        String playerName,
        String club,
        String role,
        String season,
        String fantacalcioExternalId,
        Long currentBatchId,
        Long previousBatchId,
        OffsetDateTime observedAt,
        String discoveryStatus,
        boolean officialTransferEventPresent
) {
}