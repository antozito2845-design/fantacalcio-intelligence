package it.fantacalciointelligence.backend.freshness;

import java.time.OffsetDateTime;

public record SourceFreshnessResponse(
        String source,
        Long batchId,
        OffsetDateTime observedAt,
        Long ageHours,
        int freshMaxHours,
        int staleAfterHours,
        boolean requiredForRefresh,
        String status
) {
}