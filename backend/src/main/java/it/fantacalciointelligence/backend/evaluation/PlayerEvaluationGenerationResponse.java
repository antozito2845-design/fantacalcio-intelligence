package it.fantacalciointelligence.backend.evaluation;

public record PlayerEvaluationGenerationResponse(
        String season,
        int eligiblePlayers,
        int generatedEvaluations,
        String status
) {
}
