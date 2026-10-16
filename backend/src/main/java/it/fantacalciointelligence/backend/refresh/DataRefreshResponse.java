package it.fantacalciointelligence.backend.refresh;

import java.time.OffsetDateTime;

public record DataRefreshResponse(
        Long id,
        String season,
        String triggerType,
        String status,
        String sourceFreshnessStatus,
        OffsetDateTime sourceFreshnessCheckedAt,
        int reconciliationMatchedPlayers,
        int generatedEvaluations,
        int generatedClubRatings,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        String errorMessage
) {
}