package it.fantacalciointelligence.backend.squad;

public record SquadRoleSummaryResponse(
        int current,
        int maximum,
        int remaining
) {
}