package it.fantacalciointelligence.backend.liveauction;

import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.squadconstruction.DynamicBudgetResponse;

@Component
public class LiveAuctionBudgetGuard {
    public Result evaluate(int bid, int remainingBudget, DynamicBudgetResponse budget) {
        int after = remainingBudget - bid;
        int remainingAfterPurchase = Math.max(0,
                budget.slotsRemainingByRole().values().stream().mapToInt(Integer::intValue).sum() - 1);
        boolean completionSafe = after >= remainingAfterPurchase;
        boolean roleSafe = bid <= budget.contextualMaximumBid();
        return new Result(after, remainingAfterPurchase, completionSafe && roleSafe,
                !completionSafe ? "BID_BREAKS_COMPLETION_RESERVE"
                        : !roleSafe ? "BID_EXCEEDS_B54_CONTEXTUAL_MAX" : "BUDGET_SAFE");
    }

    public record Result(int budgetAfterBid, int minimumCompletionReserve,
            boolean sustainable, String reason) { }
}
