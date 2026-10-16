package it.fantacalciointelligence.backend.squad;

import java.util.List;
import java.util.Map;

public record SquadResponse(
        Long id,
        String name,
        String season,
        int initialBudget,
        int spentBudget,
        int remainingBudget,
        int totalPlayers,
        int maximumPlayers,
        Map<String, SquadRoleSummaryResponse> roles,
        List<SquadPlayerResponse> players
) {
}