package it.fantacalciointelligence.backend.player;

import java.math.BigDecimal;

public record HistoricalPlayerCatalogResponse(
        Long id,
        String name,
        boolean active,
        String season,
        String club,
        String clubSlug,
        String fantacalcioRole,
        Integer appearances,
        BigDecimal fantasyAverage,
        Integer goals,
        Integer assists
) {
}
