package it.fantacalciointelligence.backend.player;

import java.util.List;
import java.util.Map;

public record PlayerDetailResponse(
        Long id,
        String name,
        String club,
        String clubSlug,
        Map<String, String> roles,
        PlayerTacticalProfileResponse tacticalProfile,
        ClubTacticalResponse clubTactics,
        PlayerEvaluationResponse evaluation,
        List<PlayerSeasonStatisticsResponse> statistics
) {
}