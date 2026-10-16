package it.fantacalciointelligence.backend.fixture;

public record MatchdayFixtureResponse(
        Long fixtureId,
        int matchday,
        String homeClub,
        String awayClub,
        String homeDifficulty,
        String awayDifficulty
) {
}