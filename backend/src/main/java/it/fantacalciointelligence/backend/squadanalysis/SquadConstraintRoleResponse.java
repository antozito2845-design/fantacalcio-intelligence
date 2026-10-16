package it.fantacalciointelligence.backend.squadanalysis;

import java.util.List;

public record SquadConstraintRoleResponse(
        String role,
        int currentPlayers,
        int maximumPlayers,
        int remainingSlots,
        boolean complete,
        String coverageStatus,
        List<String> warnings
) {
}