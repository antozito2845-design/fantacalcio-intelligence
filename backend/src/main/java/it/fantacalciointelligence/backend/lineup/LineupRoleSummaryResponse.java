package it.fantacalciointelligence.backend.lineup;

public record LineupRoleSummaryResponse(
        int required,
        int selected,
        int missing
) {
}