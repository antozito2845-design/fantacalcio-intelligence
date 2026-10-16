package it.fantacalciointelligence.backend.reconciliation.review;

import java.time.OffsetDateTime;

public record ReconciliationReviewItemResponse(
        Long id,
        String season,

        long fantacalcioBatchId,
        long gazzettaBatchId,
        Long fantamarktBatchId,

        String fantacalcioExternalId,
        String fantacalcioName,
        String fantacalcioClub,

        String gazzettaExternalId,
        String gazzettaName,
        String gazzettaClub,

        String fantamarktExternalId,
        String fantamarktName,
        String fantamarktClub,

        String reviewStatus,

        String resolutionSource,
        String resolutionExternalId,
        String resolutionNote,

        String exclusionReason,
        String exclusionNote,

        OffsetDateTime firstSeenAt,
        OffsetDateTime lastSeenAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime excludedAt
) {
}
