package it.fantacalciointelligence.backend.importer.admin;

import java.util.List;

public record ImportBatchDetailResponse(
        ImportBatchSummaryResponse batch,
        List<ImportRowErrorResponse> invalidRows,
        int errorPage,
        int errorSize,
        long totalInvalidRows,
        int totalErrorPages
) {
}