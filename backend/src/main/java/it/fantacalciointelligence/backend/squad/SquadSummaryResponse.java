package it.fantacalciointelligence.backend.squad;

public record SquadSummaryResponse(
        Long id,
        String name,
        String season,
        int initialBudget,
        int spentBudget,
        int remainingBudget,
        int totalPlayers,
        int maximumPlayers,
        boolean active
) {
}