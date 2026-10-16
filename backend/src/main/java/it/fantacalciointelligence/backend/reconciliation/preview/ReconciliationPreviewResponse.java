package it.fantacalciointelligence.backend.reconciliation.preview;

import java.util.List;
import java.util.Map;

public record ReconciliationPreviewResponse(
        long fantacalcioBatchId,
        long gazzettaBatchId,
        Long fantamarktBatchId,

        int totalFantacalcioPlayers,
        int confirmedPlayers,
        int unresolvedPlayers,

        Map<String, Integer> methodCounts,

        List<ReconciliationPreviewPlayer> players
) {
}