package it.fantacalciointelligence.backend.statistics.importer;

public record PlayerStatisticsImportResponse(
        Long batchId,
        String source,
        String season,
        String filename,
        int totalRows,
        int validRows,
        int invalidRows,
        int importedStatistics,
        String status,
        String errorMessage
) {
}