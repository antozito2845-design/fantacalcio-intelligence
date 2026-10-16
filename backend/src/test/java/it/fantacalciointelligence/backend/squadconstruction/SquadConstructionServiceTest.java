package it.fantacalciointelligence.backend.squadconstruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.auction.AuctionApiPriceService;

@SpringBootTest
@Transactional
class SquadConstructionServiceTest {
    @Autowired private SquadConstructionService service;
    @MockitoBean private AuctionApiPriceService priceService;

    @Test
    void calculatesB51MarketOnceAndDoesNotInventMissingPrices() {
        when(priceService.calculateAll("2026/2027", 1000, 8, false)).thenReturn(List.of());

        SquadConstructionResponse response = service.evaluate(new SquadConstructionRequest(
                "2026/2027", 8, 1000, 1000, false, "D", null, List.of(), 20));

        assertThat(response.recommendedTargets()).isEmpty();
        verify(priceService, times(1)).calculateAll("2026/2027", 1000, 8, false);
    }

    @Test
    void missingProfileNeedsChangeWithRosterCoverage() {
        DynamicBudgetResponse budget = new DynamicBudgetEngine().calculate(
                new SquadConstructionRequest(
                        "2026/2027", 8, 1000, 1000,
                        false, "D", null, List.of(), 20
                )
        );
        List<String> missing = service.missingProfileNeeds(
                new RosterProfileContext(Map.of(), 0, 4, false),
                "D",
                budget
        );
        List<String> covered = service.missingProfileNeeds(
                new RosterProfileContext(
                        Map.of(
                                "PREMIUM", 1,
                                "BONUS_SPECIALIST", 1,
                                "STARTER", 2,
                                "UPSIDE_BET", 1
                        ),
                        0,
                        3,
                        false
                ),
                "D",
                budget
        );

        assertThat(missing)
                .contains("MANCA_PREMIUM_O_SEMI_TOP")
                .contains("SERVE_VALUE_DEPTH");
        assertThat(covered)
                .contains("QUALITA_PREMIUM_COPERTA")
                .doesNotContain("MANCA_PREMIUM_O_SEMI_TOP")
                .doesNotContain("MANCA_BONUS_SPECIALIST");
    }
}
