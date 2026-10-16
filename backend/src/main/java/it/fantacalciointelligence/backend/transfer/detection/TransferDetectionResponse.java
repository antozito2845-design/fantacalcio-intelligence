package it.fantacalciointelligence.backend.transfer.detection;

import java.util.List;

public record TransferDetectionResponse(
        String season,
        long fantacalcioBatchId,
        Long gazzettaBatchId,
        Long fantamarktBatchId,
        int detectedClubChanges,
        int detectedSerieAExits,
        int createdClaims,
        int corroboratedClaims,
        int existingOpenClaims,
        int staleClaimsSkipped,
        int missingFromFantacalcio,
        List<Long> createdClaimIds
) {
}