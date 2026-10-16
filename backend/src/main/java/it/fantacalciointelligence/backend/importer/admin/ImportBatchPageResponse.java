package it.fantacalciointelligence.backend.importer.admin;

import java.util.List;

public record ImportBatchPageResponse(
        List<ImportBatchSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}