package it.fantacalciointelligence.backend.identity.enrichment;

public record IdentityEnrichmentImportResponse(
        int totalRows,
        int importedRows,
        int verifiedComplete,
        int verifiedPartial,
        int noHistoryExpected,
        int reviewRequired,
        int dataSourceMissing
) {}
