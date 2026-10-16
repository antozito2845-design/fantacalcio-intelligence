package it.fantacalciointelligence.backend.importer.admin;

import java.time.OffsetDateTime;

public record ImportBatchSummaryResponse(
        Long id,
        String source,
        String season,
        String filename,
        String status,
        int totalRows,
        int validRows,
        int invalidRows,
        int reconciledRows,
        OffsetDateTime startedAt,
        OffsetDateTime completedAt,
        OffsetDateTime reconciliationStartedAt,
        OffsetDateTime reconciliationCompletedAt,
        String errorMessage
) {
}