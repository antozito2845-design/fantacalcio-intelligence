package it.fantacalciointelligence.backend.squadconstruction;

import java.util.Map;

public record DynamicBudgetResponse(
        int minimumCompletionReserve,
        int futureBudgetProtected,
        int currentRoleMinimumReserve,
        int currentRoleBudgetAvailable,
        int contextualMaximumBid,
        Map<String, Integer> slotsFilledByRole,
        Map<String, Integer> slotsRemainingByRole,
        Map<String, Integer> remainingBudgetTargets,
        int savingsOrOverspend
) {
}
