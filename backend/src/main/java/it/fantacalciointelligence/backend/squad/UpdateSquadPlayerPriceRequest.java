package it.fantacalciointelligence.backend.squad;

import jakarta.validation.constraints.Min;

public record UpdateSquadPlayerPriceRequest(
        @Min(1)
        int purchasePrice
) {
}
