package it.fantacalciointelligence.backend.identity.enrichment;

public record IdentityEnrichmentReadinessResponse(
        String status,
        int operationalPlayers,
        int profiledPlayers,
        int verifiedComplete,
        int verifiedPartial,
        int noHistoryExpected,
        int reviewRequired,
        int dataSourceMissing,
        int canonicalNames,
        int birthDates,
        int nationalities
) {}
