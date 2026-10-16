package it.fantacalciointelligence.backend.fixture;

import java.math.BigDecimal;

public record B69RoleDifficultyResponse(
        String role,
        BigDecimal difficultyScore,
        String difficultyLevel,
        String evidenceTier,
        BigDecimal evidenceCoverage
) {
}