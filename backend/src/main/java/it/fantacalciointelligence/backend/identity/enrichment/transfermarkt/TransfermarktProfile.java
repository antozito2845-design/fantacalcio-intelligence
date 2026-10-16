package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import java.time.LocalDate;

public record TransfermarktProfile(
        String transfermarktPlayerId,
        String fullName,
        LocalDate birthDate,
        String nationality,
        Integer heightCm,
        String preferredFoot,
        String currentClub,
        String profileUrl
) {
}