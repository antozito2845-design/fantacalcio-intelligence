package it.fantacalciointelligence.backend.liveauction;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.squadconstruction.BoughtPlayerRequest;
import it.fantacalciointelligence.backend.squadconstruction.DynamicBudgetEngine;
import it.fantacalciointelligence.backend.squadconstruction.SquadConstructionRequest;

class LiveAuctionBudgetGuardTest {
    private final DynamicBudgetEngine b54 = new DynamicBudgetEngine();
    private final LiveAuctionBudgetGuard guard = new LiveAuctionBudgetGuard();

    @Test
    void doesNotAddFutureReserveTwice() {
        var budget = b54.calculate(new SquadConstructionRequest("2026/2027", 8, 1000, 990,
                false, "D", null, List.of(new BoughtPlayerRequest(1L, "P", 10)), 20));
        var result = guard.evaluate(budget.contextualMaximumBid(), 990, budget);
        assertThat(result.sustainable()).isTrue();
        assertThat(result.minimumCompletionReserve())
                .isEqualTo(budget.minimumCompletionReserve() - 1);
    }

    @Test
    void lastRosterSlotHasNoNonexistentFutureReserve() {
        var bought = new java.util.ArrayList<BoughtPlayerRequest>();
        long id = 1;
        for (String role : List.of("P", "P", "P", "D", "D", "D", "D", "D", "D", "D", "D",
                "C", "C", "C", "C", "C", "C", "C", "C", "A", "A", "A", "A", "A")) {
            bought.add(new BoughtPlayerRequest(id++, role, 1));
        }
        var budget = b54.calculate(new SquadConstructionRequest("2026/2027", 8, 150, 126,
                false, "A", null, bought, 20));
        assertThat(budget.futureBudgetProtected()).isZero();
        assertThat(budget.contextualMaximumBid()).isEqualTo(126);
        assertThat(guard.evaluate(126, 126, budget).sustainable()).isTrue();
    }
}
