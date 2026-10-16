package it.fantacalciointelligence.backend.importer;

public record PlayerImportResponse(
        Long batchId,
        String source,
        String season,
        String filename,
        int totalRows,
        int validRows,
        int invalidRows,
        String status,
        String errorMessage
) {
}