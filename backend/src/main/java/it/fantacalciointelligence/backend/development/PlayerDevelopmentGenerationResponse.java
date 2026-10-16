package it.fantacalciointelligence.backend.development;

public record PlayerDevelopmentGenerationResponse(
        String targetSeason,
        int eligiblePlayers,
        int generatedAssessments,
        int growingPlayers,
        int stablePlayers,
        int decliningPlayers,
        int insufficientDataPlayers,
        String status
) {
}