package it.fantacalciointelligence.backend.squadconstruction;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;

public record BoughtPlayerRequest(
        @NotNull @Positive Long playerId,
        @NotBlank @Pattern(regexp = "P|D|C|A") String role,
        @Min(1) int pricePaid
) {
}
