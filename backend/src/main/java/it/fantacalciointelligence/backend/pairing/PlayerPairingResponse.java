package it.fantacalciointelligence.backend.pairing;

import java.math.BigDecimal;

public record PlayerPairingResponse(
        int position,
        PairingPlayerResponse firstPlayer,
        PairingPlayerResponse secondPlayer,
        int combinedRecommendedPrice,
        BigDecimal pairingScore,
        String recommendation,
        String explanation
) {
}