package it.fantacalciointelligence.backend.player;

public record PlayerCatalogResponse(
        Long id,
        String name,
        String club,
        String clubSlug,
        String fantacalcioRole,
        String gazzettaRole,
        String fantamarktRole,
        int sourcesCount
) {
}