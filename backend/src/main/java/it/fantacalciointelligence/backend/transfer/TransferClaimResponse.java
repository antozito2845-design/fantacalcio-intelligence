package it.fantacalciointelligence.backend.transfer;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TransferClaimResponse(
        Long id,
        Long playerId,
        String playerName,
        String season,
        String fromClub,
        String toClub,
        String destinationClubName,
        String source,
        String sourceReference,
        OffsetDateTime sourcePublishedAt,
        String verificationSource,
        String verificationReference,
        OffsetDateTime verificationPublishedAt,
        OffsetDateTime observedAt,
        LocalDate effectiveAt,
        String verificationStatus,
        OffsetDateTime verifiedAt,
        String rejectionReason
) {
}
