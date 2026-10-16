package it.fantacalciointelligence.backend.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

class AuctionApiPriceServiceTest {

    @Test
    void shouldCalculateCurrentB67PlayerPrice() {

        B67AuctionPriceModelService modelService =
                mock(B67AuctionPriceModelService.class);

        AuctionApiPriceService service =
                new AuctionApiPriceService(
                        modelService
                );

        B67AuctionPriceModelService.AuctionPriceModelResult modelResult =
                pricedResult(
                        10L,
                        "Paz N.",
                        "Como",
                        "C",
                        91.25,
                        1,
                        97.10,
                        150,
                        175,
                        200,
                        "HIGH_EVIDENCE"
                );

        when(
                modelService.calculateAll(
                        "2026/2027",
                        1000,
                        8,
                        false
                )
        ).thenReturn(
                List.of(modelResult)
        );

        AuctionPriceResponse response =
                service.calculate(
                        10L,
                        "2026/2027",
                        1000,
                        8,
                        false
                );

        assertThat(response.playerId())
                .isEqualTo(10L);

        assertThat(response.playerName())
                .isEqualTo("Paz N.");

        assertThat(response.role())
                .isEqualTo("C");

        assertThat(response.minimumPrice())
                .isEqualTo(150);

        assertThat(response.recommendedPrice())
                .isEqualTo(175);

        assertThat(response.maximumPrice())
                .isEqualTo(200);

        assertThat(response.calculationScore())
                .isEqualByComparingTo(
                        new BigDecimal("91.25")
                );

        assertThat(response.recommendation())
                .isEqualTo("PRICED");

        assertThat(response.explanation())
                .contains(
                        "B67_AUCTION_PRICE_V1"
                )
                .contains(
                        "HIGH_EVIDENCE"
                );

        verify(modelService)
                .calculateAll(
                        "2026/2027",
                        1000,
                        8,
                        false
                );
    }

    @Test
    void shouldExposeMarketOnlyOperationalPlayer() {

        B67AuctionPriceModelService modelService =
                mock(B67AuctionPriceModelService.class);

        AuctionApiPriceService service =
                new AuctionApiPriceService(
                        modelService
                );

        B67AuctionPriceModelService.AuctionPriceModelResult penev =
                new B67AuctionPriceModelService.AuctionPriceModelResult(
                        1039L,
                        "Penev",
                        "Lecce",
                        "P",

                        0.0,
                        0,
                        "NO_HISTORY",

                        1.0,
                        1.0,

                        10.0,
                        10.0,

                        null,
                        null,
                        null,
                        null,

                        10.0,
                        0,
                        "MARKET_ONLY",

                        10.0,
                        58,
                        0.0,

                        "PRICED",

                        1,
                        1,
                        1,

                        new BigDecimal("0.100")
                );

        when(
                modelService.calculateAll(
                        "2026/2027",
                        1000,
                        8,
                        false
                )
        ).thenReturn(
                List.of(penev)
        );

        AuctionPriceResponse response =
                service.calculate(
                        1039L,
                        "2026/2027",
                        1000,
                        8,
                        false
                );

        assertThat(response.playerId())
                .isEqualTo(1039L);

        assertThat(response.recommendedPrice())
                .isEqualTo(1);

        assertThat(response.explanation())
                .contains("MARKET_ONLY");

        assertThat(response.explanation())
                .contains("B67_AUCTION_PRICE_V1");
    }

    @Test
    void shouldThrowNotFoundForPlayerOutsideCurrentResult() {

        B67AuctionPriceModelService modelService =
                mock(B67AuctionPriceModelService.class);

        AuctionApiPriceService service =
                new AuctionApiPriceService(
                        modelService
                );

        when(
                modelService.calculateAll(
                        "2026/2027",
                        1000,
                        8,
                        false
                )
        ).thenReturn(
                List.of()
        );

        assertThatThrownBy(() ->
                service.calculate(
                        999999L,
                        "2026/2027",
                        1000,
                        8,
                        false
                )
        )
                .isInstanceOf(
                        ResourceNotFoundException.class
                )
                .hasMessage(
                        "Giocatore non trovato"
                );
    }

    @Test
    void calculateAllShouldReturnEveryPricedB67Player() {

        B67AuctionPriceModelService modelService =
                mock(B67AuctionPriceModelService.class);

        AuctionApiPriceService service =
                new AuctionApiPriceService(
                        modelService
                );

        B67AuctionPriceModelService.AuctionPriceModelResult first =
                pricedResult(
                        10L,
                        "Paz N.",
                        "Como",
                        "C",
                        91.25,
                        1,
                        97.10,
                        150,
                        175,
                        200,
                        "HIGH_EVIDENCE"
                );

        B67AuctionPriceModelService.AuctionPriceModelResult second =
                pricedResult(
                        20L,
                        "Dimarco",
                        "Inter",
                        "D",
                        94.10,
                        1,
                        99.00,
                        50,
                        61,
                        70,
                        "HIGH_EVIDENCE"
                );

        when(
                modelService.calculateAll(
                        "2026/2027",
                        1000,
                        8,
                        false
                )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );

        List<AuctionPriceResponse> responses =
                service.calculateAll(
                        "2026/2027",
                        1000,
                        8,
                        false
                );

        assertThat(responses)
                .hasSize(2);

        assertThat(responses)
                .extracting(
                        AuctionPriceResponse::playerId
                )
                .containsExactly(
                        10L,
                        20L
                );
    }

    private B67AuctionPriceModelService.AuctionPriceModelResult pricedResult(
            Long playerId,
            String name,
            String club,
            String role,
            double auctionScore,
            int auctionRank,
            double auctionPercentile,
            int minimumPrice,
            int recommendedPrice,
            int maximumPrice,
            String evidenceTier
    ) {

        return new B67AuctionPriceModelService.AuctionPriceModelResult(
                playerId,
                name,
                club,
                role,

                87.50,
                2,
                "TWO_FANTACALCIO",

                20.0,
                100.0,

                90.0,
                85.0,

                80.0,
                75.0,
                70.0,
                65.0,

                87.50,
                4,
                evidenceTier,

                auctionScore,
                auctionRank,
                auctionPercentile,

                "PRICED",

                minimumPrice,
                recommendedPrice,
                maximumPrice,

                BigDecimal
                        .valueOf(
                                recommendedPrice
                                        / 10.0
                        )
                        .setScale(3)
        );
    }
}