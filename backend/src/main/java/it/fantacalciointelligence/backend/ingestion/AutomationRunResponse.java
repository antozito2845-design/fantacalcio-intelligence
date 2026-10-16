package it.fantacalciointelligence.backend.ingestion;

import java.time.OffsetDateTime;

public record AutomationRunResponse(
        Long id,
        String season,
        String triggerType,
        String status,
        String stage,
        Long listoneRunId,
        int ingestionRunCount,
        Long refreshRunId,
        OffsetDateTime startedAt,
        OffsetDateTime lastHeartbeatAt,
        OffsetDateTime completedAt,
        String errorMessage
) {
}