package it.fantacalciointelligence.backend.player;

import java.util.List;
import java.util.Map;

public record HistoricalPlayerDetailResponse(
        Long id,
        String name,
        boolean active,
        String season,
        String club,
        String clubSlug,
        Map<String, String> roles,
        List<PlayerSeasonStatisticsResponse> statistics
) {
}
