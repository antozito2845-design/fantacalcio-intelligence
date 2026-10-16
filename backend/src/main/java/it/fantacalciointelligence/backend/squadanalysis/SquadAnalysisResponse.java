package it.fantacalciointelligence.backend.squadanalysis;

import java.util.List;

public record SquadAnalysisResponse(
        Long squadId,
        String squadName,
        String season,

        int fromMatchday,
        int toMatchday,

        int initialBudget,
        int spentBudget,
        int remainingBudget,

        int totalPlayers,
        int maximumPlayers,
        int remainingSlots,

        boolean constraintsValid,
        boolean squadComplete,

        String summary,

        List<SquadConstraintRoleResponse> roles,
        List<SquadCalendarPlayerResponse> players,
        List<SquadPairingResponse> pairings,
        List<String> globalWarnings
) {
}