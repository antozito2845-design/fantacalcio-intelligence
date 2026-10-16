package it.fantacalciointelligence.backend.squadconstruction;

import java.util.List;

public record SquadConstructionResponse(
        String season,
        int teams,
        int initialBudget,
        int remainingBudget,
        String currentPhaseRole,
        boolean defenseModifier,
        DynamicBudgetResponse budget,
        List<String> missingProfileNeeds,
        List<SquadTargetResponse> recommendedTargets,
        List<SquadTargetResponse> valueTargets,
        List<SquadTargetResponse> upsideTargets,
        List<SquadTargetResponse> avoidOverpayTargets,
        List<String> strategyAlternatives
) {
}
