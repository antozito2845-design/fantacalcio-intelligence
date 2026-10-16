package it.fantacalciointelligence.backend.squad;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddSquadPlayerRequest(

        @NotNull
        Long playerId,

        @Min(1)
        int purchasePrice
) {
}