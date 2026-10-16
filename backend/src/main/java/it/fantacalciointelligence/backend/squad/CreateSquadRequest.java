package it.fantacalciointelligence.backend.squad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSquadRequest(
        @NotNull @Positive Long leagueConfigurationId,
        @NotBlank @Size(max = 100) String name,
        boolean active
) {
}
