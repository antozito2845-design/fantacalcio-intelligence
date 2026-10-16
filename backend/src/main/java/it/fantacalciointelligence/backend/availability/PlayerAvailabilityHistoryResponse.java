package it.fantacalciointelligence.backend.availability;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PlayerAvailabilityHistoryResponse(
        Long id,
        Long availabilityId,
        Long playerId,
        String playerName,
        String season,
        int matchday,
        String status,
        BigDecimal startingProbability,
        BigDecimal availabilityProbability,
        String injuryDescription,
        LocalDate expectedReturnDate,
        String source,
        Instant changedAt
) {
}