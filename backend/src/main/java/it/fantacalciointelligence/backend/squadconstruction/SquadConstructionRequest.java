package it.fantacalciointelligence.backend.squadconstruction;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;

public record SquadConstructionRequest(
        @NotBlank String season,
        @Min(6) @Max(12) int teams,
        @Min(150) @Max(1000) int initialBudget,
        @Min(0) int remainingBudget,
        boolean defenseModifier,
        @NotBlank @Pattern(regexp = "P|D|C|A") String currentPhaseRole,
        @NotNull @NotEmpty Map<String, Integer> rosterSlots,
        @NotNull List<@Valid BoughtPlayerRequest> playersAlreadyBought,
        @Min(1) @Max(100) int limit
) {
    @AssertTrue(message = "remainingBudget non può superare initialBudget")
    public boolean isBudgetConsistent() {
        return remainingBudget <= initialBudget;
    }

    @AssertTrue(message = "rosterSlots deve contenere solo P, D, C, A con valori non negativi")
    public boolean isRosterSlotsValid() {
        return rosterSlots != null && rosterSlots.entrySet().stream()
                .allMatch(entry -> List.of("P", "D", "C", "A").contains(entry.getKey())
                        && entry.getValue() != null && entry.getValue() >= 0);
    }
}
