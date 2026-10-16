package it.fantacalciointelligence.backend.squadconstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.common.BadRequestException;

class DynamicBudgetEngineTest {
    private final DynamicBudgetEngine engine = new DynamicBudgetEngine();

    @Test
    void savingsIncreaseFutureAvailabilityAndOverspendReducesIt() {
        DynamicBudgetResponse saved = engine.calculate(request("D", 930,
                List.of(new BoughtPlayerRequest(1L, "P", 40))));
        DynamicBudgetResponse overspent = engine.calculate(request("D", 850,
                List.of(new BoughtPlayerRequest(1L, "P", 120))));
        assertThat(saved.savingsOrOverspend()).isGreaterThan(overspent.savingsOrOverspend());
        assertThat(saved.remainingBudgetTargets().get("A")).isGreaterThan(overspent.remainingBudgetTargets().get("A"));
    }

    @Test
    void protectsOneCreditForEveryMissingSlotInEveryPhase() {
        for (String phase : List.of("P", "D", "C", "A")) {
            DynamicBudgetResponse result = engine.calculate(request(phase, 1000, List.of()));
            assertThat(result.minimumCompletionReserve()).isEqualTo(25);
            assertThat(result.contextualMaximumBid()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    void modifierMovesMoreBudgetToDefenseWithoutChangingInputBudget() {
        DynamicBudgetResponse plain = engine.calculate(request(false, "D", 1000, List.of()));
        DynamicBudgetResponse modifier = engine.calculate(request(true, "D", 1000, List.of()));
        assertThat(modifier.remainingBudgetTargets().get("D")).isGreaterThan(plain.remainingBudgetTargets().get("D"));
        assertThat(modifier.remainingBudgetTargets().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(1000);
    }

    @Test
    void validatesBoundariesAndImpossibleCompletion() {
        assertThat(engine.calculate(new SquadConstructionRequest("2026/2027", 6, 150, 150, false,
                "P", null, List.of(), 10))).isNotNull();
        assertThat(engine.calculate(new SquadConstructionRequest("2026/2027", 12, 1000, 1000, false,
                "A", null, List.of(), 10))).isNotNull();
        assertThatThrownBy(() -> engine.calculate(request("P", 20, List.of())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("insufficiente");
    }

    @Test
    void completeRosterHasNoReserveAndNoBid() {
        List<BoughtPlayerRequest> players = new java.util.ArrayList<>();
        long id = 1;
        for (var entry : Map.of("P", 3, "D", 8, "C", 8, "A", 6).entrySet())
            for (int i = 0; i < entry.getValue(); i++) players.add(new BoughtPlayerRequest(id++, entry.getKey(), 1));
        DynamicBudgetResponse result = engine.calculate(request("A", 975, players));
        assertThat(result.minimumCompletionReserve()).isZero();
        assertThat(result.contextualMaximumBid()).isZero();
    }

    @Test
    void defensePhaseProtectsFutureRolesOnlyOnce() {
        List<BoughtPlayerRequest> bought = bought("P", 3, 10);
        bought.addAll(bought("D", 5, 10));
        DynamicBudgetResponse result = engine.calculate(
                request("D", 920, bought)
        );

        assertThat(result.slotsRemainingByRole().get("D"))
                .isEqualTo(3);
        assertThat(result.currentRoleMinimumReserve())
                .isEqualTo(2);
        assertThat(result.contextualMaximumBid())
                .isEqualTo(
                        result.currentRoleBudgetAvailable() - 2
                );
        assertThat(result.futureBudgetProtected())
                .isEqualTo(
                        result.remainingBudgetTargets().get("C")
                                + result.remainingBudgetTargets().get("A")
                );
    }

    @Test
    void midfieldAndAttackProtectOnlyTheirOwnOtherSlots() {
        List<BoughtPlayerRequest> midfield = bought("P", 3, 1);
        midfield.addAll(bought("D", 8, 1));
        midfield.addAll(bought("C", 5, 1));
        DynamicBudgetResponse c = engine.calculate(
                request("C", 980, midfield)
        );
        assertThat(c.futureBudgetProtected())
                .isEqualTo(c.remainingBudgetTargets().get("A"));
        assertThat(c.contextualMaximumBid())
                .isEqualTo(c.currentRoleBudgetAvailable() - 2);

        List<BoughtPlayerRequest> attack = new java.util.ArrayList<>(midfield);
        attack.addAll(bought("C", 3, 1));
        attack.addAll(bought("A", 3, 1));
        DynamicBudgetResponse a = engine.calculate(
                request("A", 974, attack)
        );
        assertThat(a.futureBudgetProtected()).isZero();
        assertThat(a.contextualMaximumBid())
                .isEqualTo(a.currentRoleBudgetAvailable() - 2);
    }

    private List<BoughtPlayerRequest> bought(
            String role,
            int count,
            int price
    ) {
        List<BoughtPlayerRequest> result = new java.util.ArrayList<>();
        long base = role.charAt(0) * 100L;
        for (int index = 0; index < count; index++) {
            result.add(new BoughtPlayerRequest(base + index, role, price));
        }
        return result;
    }

    private SquadConstructionRequest request(String phase, int remaining, List<BoughtPlayerRequest> bought) {
        return request(false, phase, remaining, bought);
    }
    private SquadConstructionRequest request(boolean modifier, String phase, int remaining, List<BoughtPlayerRequest> bought) {
        return new SquadConstructionRequest("2026/2027", 8, 1000, remaining, modifier, phase, null, bought, 20);
    }
}
