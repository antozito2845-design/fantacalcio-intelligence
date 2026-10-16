package it.fantacalciointelligence.backend.availability;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PlayerAvailabilityResponse(
        Long playerId,
        String playerName,
        String club,
        String season,
        int matchday,
        String status,
        BigDecimal startingProbability,
        BigDecimal availabilityProbability,
        String injuryDescription,
        LocalDate expectedReturnDate,
        String source,
        Instant updatedAt
) {
}