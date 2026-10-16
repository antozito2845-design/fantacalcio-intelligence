package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

public record IdentityAutomationResponse(
        String season,
        String status,
        int requestedPlayers,
        int matchedVerified,
        int reviewRequired,
        int notFound,
        int failed,
        int persistedFields
) {

    public static IdentityAutomationResponse disabled(
            String season
    ) {
        return new IdentityAutomationResponse(
                season,
                "DISABLED",
                0,
                0,
                0,
                0,
                0,
                0
        );
    }
}