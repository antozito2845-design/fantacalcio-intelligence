package it.fantacalciointelligence.backend.reconciliation.identity;

import java.math.BigDecimal;

public record ResolvedPlayerIdentity(
        String normalizedName,
        int sourcesCount,
        String identityResolutionMethod,

        String fantacalcioExternalId,
        String fantacalcioName,
        String fantacalcioClub,
        String fantacalcioNormalizedClub,
        String fantacalcioRole,
        BigDecimal fantacalcioQuotation,
        BigDecimal fantacalcioFmv,

        String gazzettaExternalId,
        String gazzettaName,
        String gazzettaClub,
        String gazzettaRole,

        String fantamarktExternalId,
        String fantamarktName,
        String fantamarktClub,
        String fantamarktRole
) {
}