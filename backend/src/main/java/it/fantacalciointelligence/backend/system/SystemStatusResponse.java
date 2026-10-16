package it.fantacalciointelligence.backend.system;

import java.time.OffsetDateTime;

public record SystemStatusResponse(
        String status,
        String application,
        String version,
        String environment,
        OffsetDateTime checkedAt
) {
}
