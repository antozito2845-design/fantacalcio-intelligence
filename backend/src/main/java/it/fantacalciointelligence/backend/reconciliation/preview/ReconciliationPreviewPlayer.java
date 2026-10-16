package it.fantacalciointelligence.backend.reconciliation.preview;

public record ReconciliationPreviewPlayer(
        String status,
        String identityResolutionMethod,
        int sourcesCount,

        String fantacalcioExternalId,
        String fantacalcioName,
        String fantacalcioClub,

        String gazzettaExternalId,
        String gazzettaName,
        String gazzettaClub,

        String fantamarktExternalId,
        String fantamarktName,
        String fantamarktClub
) {
}