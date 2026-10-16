package it.fantacalciointelligence.backend.fixture.importer;

public record FixtureImportResponse(
        Long batchId,
        String source,
        String season,
        String filename,
        int totalRows,
        int validRows,
        int invalidRows,
        int importedFixtures,
        String status,
        String errorMessage
) {
}