package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

public record TransfermarktPersistenceResponse(
        String season,
        String club,
        int requestedPlayers,
        int matchedVerified,
        int reviewRequired,
        int notFound,
        int failed,
        int persistedFields) {
}