package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

public record TransfermarktCandidate(
        String transfermarktPlayerId,
        String name,
        String url,
        String context
) {
}