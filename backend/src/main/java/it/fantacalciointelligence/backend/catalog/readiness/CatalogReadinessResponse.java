package it.fantacalciointelligence.backend.catalog.readiness;

import java.math.BigDecimal;
import java.util.List;

import it.fantacalciointelligence.backend.freshness.DataFreshnessResponse;

public record CatalogReadinessResponse(
        String season,

        long fantacalcioBatchId,
        long gazzettaBatchId,
        Long fantamarktBatchId,

        int totalFantacalcioPlayers,
        int confirmedPlayers,
        int unresolvedPlayers,

        int activeCatalogPlayers,
        int playersWithoutFantacalcioRole,
        int playersWithoutActiveRegistration,

        int pendingTransferClaims,
        int officialSerieAExits,
        int missingFromFantacalcio,

        BigDecimal qualityPercentage,

        DataFreshnessResponse dataFreshness,

        String readinessStatus,
        List<String> blockers,
        List<String> warnings,

        String status,
        int totalOperationalPlayers,
        int totalClubs,
        CatalogRoleSummary playersByRole,
        int errorCount,
        int warningCount,
        List<CatalogQualityIssue> issues,
        List<CatalogClubSummary> clubSummaries
) {

    public boolean blocked() {
        return "BLOCKED".equals(
                readinessStatus
        );
    }
}
