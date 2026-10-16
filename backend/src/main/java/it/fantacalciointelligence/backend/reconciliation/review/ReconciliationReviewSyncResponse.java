package it.fantacalciointelligence.backend.reconciliation.review;

public record ReconciliationReviewSyncResponse(
        String season,
        long fantacalcioBatchId,
        long gazzettaBatchId,
        Long fantamarktBatchId,
        int totalFantacalcioPlayers,
        int unresolvedPlayers,
        int synchronizedItems,
        int openItems
) {
}