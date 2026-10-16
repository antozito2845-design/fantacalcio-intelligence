package it.fantacalciointelligence.backend.squadanalysis;

import java.math.BigDecimal;
import java.util.List;

import it.fantacalciointelligence.backend.fixture.FixtureDifficultyResponse;

public record SquadCalendarPlayerResponse(
        Long playerId,
        String playerName,
        String club,
        String clubSlug,
        String role,
        int purchasePrice,

        BigDecimal averageDifficulty,
        int easyFixtures,
        int mediumFixtures,
        int difficultFixtures,

        List<FixtureDifficultyResponse> fixtures
) {
}