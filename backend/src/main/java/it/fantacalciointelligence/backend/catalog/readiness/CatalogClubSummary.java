package it.fantacalciointelligence.backend.catalog.readiness;

public record CatalogClubSummary(
        long clubId,
        String club,
        int totalPlayers,
        int P,
        int D,
        int C,
        int A
) {
}
