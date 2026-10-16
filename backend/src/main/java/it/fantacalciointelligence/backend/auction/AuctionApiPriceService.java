package it.fantacalciointelligence.backend.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class AuctionApiPriceService {

    private final B67AuctionPriceModelService modelService;

    public AuctionApiPriceService(
            B67AuctionPriceModelService modelService
    ) {
        this.modelService = modelService;
    }

    /**
     * Public Auction API adapter.
     *
     * B67 is the current read model.
     * B51 remains immutable historical evidence.
     *
     * Public GET endpoints remain read-only and have no
     * persistence side effects.
     */
    @Transactional(readOnly = true)
    public AuctionPriceResponse calculate(
            Long playerId,
            String seasonCode,
            int budget,
            int teams,
            boolean defenseModifier
    ) {

        B67AuctionPriceModelService.AuctionPriceModelResult result =
                modelService.calculateAll(
                                seasonCode,
                                budget,
                                teams,
                                defenseModifier
                        )
                        .stream()
                        .filter(item ->
                                item.playerId().equals(
                                        playerId
                                )
                        )
                        .findFirst()
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Giocatore non trovato"
                                )
                        );

        if (!"PRICED".equals(result.pricingStatus())) {
            throw new IllegalArgumentException(
                    "Prezzo asta non disponibile: evidenza insufficiente"
            );
        }

        return toResponse(
                result,
                teams,
                budget,
                defenseModifier
        );
    }

    /**
     * Calculates the complete current B67 auction market once.
     * Used by ranking and downstream intelligence.
     */
    @Transactional(readOnly = true)
    public List<AuctionPriceResponse> calculateAll(
            String seasonCode,
            int budget,
            int teams,
            boolean defenseModifier
    ) {

        return modelService.calculateAll(
                        seasonCode,
                        budget,
                        teams,
                        defenseModifier
                )
                .stream()
                .filter(result ->
                        "PRICED".equals(
                                result.pricingStatus()
                        )
                )
                .map(result ->
                        toResponse(
                                result,
                                teams,
                                budget,
                                defenseModifier
                        )
                )
                .toList();
    }

    private AuctionPriceResponse toResponse(
            B67AuctionPriceModelService.AuctionPriceModelResult result,
            int teams,
            int budget,
            boolean defenseModifier
    ) {

        BigDecimal calculationScore =
                BigDecimal
                        .valueOf(
                                result.auctionScore()
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        String explanation =
                String.format(
                        "Prezzo calcolato con %s. "
                                + "Score asta %.2f/100, "
                                + "rank ruolo %d, "
                                + "percentile ruolo %.2f/100, "
                                + "evidenza %s.",
                        B67AuctionPriceModelService.ALGORITHM_VERSION,
                        result.auctionScore(),
                        result.auctionRoleRank(),
                        result.auctionRolePercentile(),
                        result.evidenceTier()
                );

        return new AuctionPriceResponse(
                result.playerId(),
                result.playerName(),
                result.club(),
                result.role(),
                teams,
                budget,
                defenseModifier,
                result.minimumPrice(),
                result.recommendedPrice(),
                result.maximumPrice(),
                result.budgetPercentage(),
                calculationScore,
                result.pricingStatus(),
                explanation
        );
    }
}
