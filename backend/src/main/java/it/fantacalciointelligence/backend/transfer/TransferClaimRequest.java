package it.fantacalciointelligence.backend.transfer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.AssertTrue;

public record TransferClaimRequest(
        @NotNull @Positive Long playerId,
        @NotBlank @Size(max = 20) String season,
        String targetClub,
        boolean leavingSerieA,
        String destinationClubName,
        @NotBlank @Size(max = 50) String source,
        @NotBlank @Size(max = 1000) String sourceReference,
        @NotNull OffsetDateTime sourcePublishedAt,
        @NotNull LocalDate effectiveAt
) {
    @AssertTrue(message = "Destinazione trasferimento incoerente")
    public boolean isDestinationConsistent() {
        if (leavingSerieA) {
            return (targetClub == null || targetClub.isBlank())
                    && destinationClubName != null && !destinationClubName.isBlank();
        }
        return targetClub != null && !targetClub.isBlank()
                && (destinationClubName == null || destinationClubName.isBlank());
    }
}
