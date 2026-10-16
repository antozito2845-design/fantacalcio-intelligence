package it.fantacalciointelligence.backend.fixture;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record FixtureDifficultyResponse(
        Long fixtureId,
        int matchday,
        OffsetDateTime kickoffAt,
        String club,
        String opponent,
        boolean home,
        BigDecimal difficultyScore,
        String difficultyLevel
) {
}