package it.fantacalciointelligence.backend.fixture.strength;

public record ClubStrengthGenerationResponse(
        String season,
        int eligiblePlayers,
        int eligibleClubs,
        int generatedRatings,
        String status
) {
}