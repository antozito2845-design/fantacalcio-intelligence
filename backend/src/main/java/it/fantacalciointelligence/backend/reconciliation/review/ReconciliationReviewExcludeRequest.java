package it.fantacalciointelligence.backend.reconciliation.review;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReconciliationReviewExcludeRequest(
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 2000) String note
) {
}
