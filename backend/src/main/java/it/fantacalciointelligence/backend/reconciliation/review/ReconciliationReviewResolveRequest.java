package it.fantacalciointelligence.backend.reconciliation.review;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconciliationReviewResolveRequest(
        @NotBlank @Size(max = 30) String secondarySource,
        @NotBlank @Size(max = 255) String secondaryExternalId,
        @Size(max = 2000) String note
) {
}
