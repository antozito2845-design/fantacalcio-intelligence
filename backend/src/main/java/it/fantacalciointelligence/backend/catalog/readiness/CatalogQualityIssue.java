package it.fantacalciointelligence.backend.catalog.readiness;

import java.util.Map;

public record CatalogQualityIssue(
        String severity,
        String code,
        String message,
        Long playerId,
        String fantacalcioExternalId,
        String playerName,
        String club,
        Map<String, Object> details
) {
}
