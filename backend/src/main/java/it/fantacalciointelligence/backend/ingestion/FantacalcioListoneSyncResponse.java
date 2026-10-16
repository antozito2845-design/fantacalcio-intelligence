package it.fantacalciointelligence.backend.ingestion;

import java.time.OffsetDateTime;

public record FantacalcioListoneSyncResponse(
        Long runId,
        String season,
        String status,
        int parsedPlayers,
        Long importBatchId,
        String checksum,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt
) {
}