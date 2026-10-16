package it.fantacalciointelligence.backend.transfer;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OfficialTransferVerificationRequest(
        @NotBlank @Size(max = 50) String source,
        @NotBlank @Size(max = 1000) String sourceReference,
        @NotNull OffsetDateTime sourcePublishedAt,
        @NotNull LocalDate effectiveAt
) {
}
