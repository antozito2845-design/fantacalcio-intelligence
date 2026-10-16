package it.fantacalciointelligence.backend.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class AuctionPriceControllerTest {

    @Test
    void priceEndpointShouldDelegateToB51ApiService() {
        AuctionApiPriceService auctionApiPriceService =
                mock(AuctionApiPriceService.class);

        AuctionRankingService auctionRankingService =
                mock(AuctionRankingService.class);

        AuctionPriceController controller =
                new AuctionPriceController(
                        auctionApiPriceService,
                        auctionRankingService
                );

        AuctionPriceResponse expected =
                new AuctionPriceResponse(
                        10L,
                        "Paz N.",
                        "Como",
                        "C",
                        8,
                        1000,
                        false,
                        150,
                        175,
                        200,
                        new BigDecimal("17.5000"),
                        new BigDecimal("100.00"),
                        "PRICED",
                        "B51"
                );

        when(
                auctionApiPriceService.calculate(
                        10L,
                        "2026/2027",
                        1000,
                        8,
                        false
                )
        ).thenReturn(expected);

        AuctionPriceResponse actual =
                controller.calculatePrice(
                        10L,
                        "2026/2027",
                        1000,
                        8,
                        false
                );

        assertThat(actual)
                .isSameAs(expected);

        verify(auctionApiPriceService)
                .calculate(
                        10L,
                        "2026/2027",
                        1000,
                        8,
                        false
                );
    }

    @Test
    void rankingEndpointShouldPreservePublicParameters() {
        AuctionApiPriceService auctionApiPriceService =
                mock(AuctionApiPriceService.class);

        AuctionRankingService auctionRankingService =
                mock(AuctionRankingService.class);

        AuctionPriceController controller =
                new AuctionPriceController(
                        auctionApiPriceService,
                        auctionRankingService
                );

        List<AuctionRankingResponse> expected =
                List.of(
                        new AuctionRankingResponse(
                                1,
                                20L,
                                "Dimarco",
                                "Inter",
                                "D",
                                50,
                                61,
                                70,
                                new BigDecimal("6.1000"),
                                new BigDecimal("99.21"),
                                "PRICED"
                        )
                );

        when(
                auctionRankingService.calculateRanking(
                        "2026/2027",
                        1000,
                        8,
                        false,
                        "D",
                        "inter",
                        25
                )
        ).thenReturn(expected);

        List<AuctionRankingResponse> actual =
                controller.calculateRanking(
                        "2026/2027",
                        1000,
                        8,
                        false,
                        "D",
                        "inter",
                        25
                );

        assertThat(actual)
                .isSameAs(expected);

        verify(auctionRankingService)
                .calculateRanking(
                        "2026/2027",
                        1000,
                        8,
                        false,
                        "D",
                        "inter",
                        25
                );
    }
}