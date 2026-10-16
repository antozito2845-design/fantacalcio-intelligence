package it.fantacalciointelligence.backend.reconciliation;

public record ReconciliationResponse(
        String season,
        long fantacalcioBatchId,
        long gazzettaBatchId,
        long fantamarktBatchId,
        int matchedPlayers,
        int matchedWithThreeSources,
        int matchedWithTwoSources,
        int excludedSingleSourcePlayers,
        int createdPlayers,
        int updatedPlayers,
        String status
) {
}