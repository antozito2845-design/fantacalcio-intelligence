package it.fantacalciointelligence.backend.liveauction;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.calendarintelligence.CalendarCandidateService;
import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.squadconstruction.BoughtPlayerRequest;
import it.fantacalciointelligence.backend.squadconstruction.SquadConstructionService;

class LiveAuctionDecisionServiceValidationTest {
    private final LiveAuctionDecisionService service = new LiveAuctionDecisionService(
            mock(LiveAuctionPlayerContextService.class), mock(SquadConstructionService.class),
            mock(CalendarCandidateService.class), mock(LiveAuctionStrategicBatchService.class),
            mock(LiveAuctionCalendarBatchService.class), mock(LiveAuctionDecisionEngine.class));

    @Test
    void missingProposedAndLegacyBidIsRejected() {
        assertThatThrownBy(() -> service.validate(request(null, 1000, List.of())))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("proposedBid");
    }

    @Test
    void bidAboveRemainingBudgetIsRejected() {
        assertThatThrownBy(() -> service.validate(new LiveAuctionDecisionRequest(
                "2026/2027", 8, 1000, 100, false, "D", 1L, 10, 101, null,
                null, List.of(new BoughtPlayerRequest(2L, "P", 900)), null, null,
                10, List.of(), List.of(), true)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("budget residuo");
    }

    @Test
    void incoherentPaidPricesAreRejected() {
        assertThatThrownBy(() -> service.validate(request(10, 990,
                List.of(new BoughtPlayerRequest(2L, "P", 9)))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("incoerente");
    }

    @Test
    void currentPlayerCannotAlreadyBeBought() {
        assertThatThrownBy(() -> service.validate(request(10, 990,
                List.of(new BoughtPlayerRequest(1L, "D", 10)))))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("gia'");
    }

    @Test
    void invalidCalendarWindowIsRejected() {
        var invalid = new LiveAuctionDecisionRequest("2026/2027", 8, 1000, 1000,
                false, "D", 1L, 10, null, List.of(), 39, 11, 10, List.of());
        assertThatThrownBy(() -> service.validate(invalid)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void proposedBidMustNotBeBelowCurrentPrice() {
        var invalid = contract(20, 19, null, List.of(), false);
        assertThatThrownBy(() -> service.validate(invalid)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inferiore a currentPrice");
    }

    @Test
    void conflictingLegacyAndCanonicalBidAreRejected() {
        var invalid = new LiveAuctionDecisionRequest("2026/2027", 8, 1000, 1000, false,
                "D", 1L, 10, 20, 21, null, List.of(), null, null, 10,
                List.of(), List.of(), true);
        assertThatThrownBy(() -> service.validate(invalid)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("conflitto");
    }

    @Test
    void malformedUnavailableIdsAreRejected() {
        assertThatThrownBy(() -> service.validate(contract(10, 20, null,
                java.util.Arrays.asList(2L, null, 2L), true)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("unavailablePlayerIds");
    }

    @Test
    void currentPlayerCannotBeUnavailable() {
        assertThatThrownBy(() -> service.validate(contract(10, 20, null, List.of(1L), true)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("giocatore corrente");
    }

    @Test
    void legacyCurrentBidIsDeterministicAliasForProposedBid() {
        var legacy = request(25, 1000, List.of());
        service.validate(legacy);
        assertThat(service.effectiveBid(legacy)).isEqualTo(25);
    }

    private LiveAuctionDecisionRequest contract(Integer currentPrice, Integer proposedBid,
            Integer legacyBid, List<Long> unavailable, boolean availabilityComplete) {
        return new LiveAuctionDecisionRequest("2026/2027", 8, 1000, 1000, false,
                "D", 1L, currentPrice, proposedBid, legacyBid, null, List.of(), null,
                null, 10, List.of(), unavailable, availabilityComplete);
    }

    private LiveAuctionDecisionRequest request(Integer bid, int remaining,
            List<BoughtPlayerRequest> bought) {
        return new LiveAuctionDecisionRequest("2026/2027", 8, 1000, remaining, false,
                "D", 1L, bid, null, bought, null, null, 10, List.of());
    }
}
