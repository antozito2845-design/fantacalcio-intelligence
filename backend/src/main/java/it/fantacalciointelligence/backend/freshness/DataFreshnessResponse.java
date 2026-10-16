package it.fantacalciointelligence.backend.freshness;

import java.time.OffsetDateTime;
import java.util.List;

public record DataFreshnessResponse(
        String season,
        OffsetDateTime evaluatedAt,
        String overallStatus,
        List<SourceFreshnessResponse> sources,
        List<String> blockers,
        List<String> warnings
) {

    public boolean blocked() {
        return "BLOCKED".equals(overallStatus);
    }
}