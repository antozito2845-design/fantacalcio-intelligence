package it.fantacalciointelligence.backend.liveauction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.calendarintelligence.CalendarCandidateService;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionPlayerContextService.PlayerContext;
import it.fantacalciointelligence.backend.squadconstruction.DynamicBudgetResponse;
import it.fantacalciointelligence.backend.squadconstruction.EvidenceStatus;
import it.fantacalciointelligence.backend.squadconstruction.SquadConstructionResponse;
import it.fantacalciointelligence.backend.squadconstruction.SquadConstructionService;
import it.fantacalciointelligence.backend.squadconstruction.SquadTargetResponse;

class LiveAuctionDecisionServiceBatchTest {
    @Test
    void buildsB54AndB56ContextOnceAndSkipsFixturesWithoutMatchday() {
        var playerContext = mock(LiveAuctionPlayerContextService.class);
        var construction = mock(SquadConstructionService.class);
        var candidates = mock(CalendarCandidateService.class);
        var strategic = mock(LiveAuctionStrategicBatchService.class);
        var calendar = mock(LiveAuctionCalendarBatchService.class);
        var engine = mock(LiveAuctionDecisionEngine.class);
        var service = new LiveAuctionDecisionService(playerContext, construction, candidates,
                strategic, calendar, engine);
        var request = new LiveAuctionDecisionRequest("2026/2027", 8, 1000, 1000,
                false, "D", 1L, 10, null, List.of(), null, null, 10, List.of());
        var target = target(1L);
        var budget = budget();
        var b54 = new SquadConstructionResponse("2026/2027", 8, 1000, 1000, "D", false,
                budget, List.of(), List.of(target), List.of(), List.of(), List.of(), List.of());
        var expected = mock(LiveAuctionDecisionResponse.class);

        when(playerContext.load(1L, "2026/2027")).thenReturn(new PlayerContext(true, true, true, "D"));
        when(construction.evaluate(any())).thenReturn(b54);
        when(candidates.loadActive("2026/2027", "D")).thenReturn(List.of());
        when(strategic.load(eq("2026/2027"), any())).thenReturn(Map.of());
        when(engine.decide(any(), eq(budget), eq(List.of()), any(), eq(List.of())))
                .thenReturn(expected);

        assertThat(service.decide(request)).isSameAs(expected);
        verify(construction, times(1)).evaluate(any());
        verify(candidates, times(1)).loadActive("2026/2027", "D");
        verify(strategic, times(1)).load(eq("2026/2027"), any());
        verify(calendar, never()).evaluate(any(), any(Integer.class), any(Integer.class),
                any(), any(), any(), any());
    }

    @Test
    void unavailableOpponentPlayerIsRemovedBeforeAlternativeEvaluation() {
        var playerContext = mock(LiveAuctionPlayerContextService.class);
        var construction = mock(SquadConstructionService.class);
        var candidates = mock(CalendarCandidateService.class);
        var strategic = mock(LiveAuctionStrategicBatchService.class);
        var calendar = mock(LiveAuctionCalendarBatchService.class);
        var engine = mock(LiveAuctionDecisionEngine.class);
        var service = new LiveAuctionDecisionService(playerContext, construction, candidates,
                strategic, calendar, engine);
        var request = new LiveAuctionDecisionRequest("2026/2027", 8, 1000, 1000, false,
                "D", 1L, 10, 20, null, null, List.of(), null, null, 10,
                List.of(), List.of(2L), true);
        var current = target(1L);
        var unavailable = target(2L);
        var b54 = new SquadConstructionResponse("2026/2027", 8, 1000, 1000, "D", false,
                budget(), List.of(), List.of(current, unavailable), List.of(), List.of(), List.of(), List.of());
        when(playerContext.load(1L, "2026/2027")).thenReturn(new PlayerContext(true, true, true, "D"));
        when(construction.evaluate(any())).thenReturn(b54);
        when(candidates.loadActive("2026/2027", "D")).thenReturn(List.of());
        when(strategic.load(eq("2026/2027"), any())).thenReturn(Map.of());
        when(engine.decide(any(), eq(budget()), eq(List.of()), any(), eq(List.of())))
                .thenReturn(mock(LiveAuctionDecisionResponse.class));

        service.decide(request);

        @SuppressWarnings("unchecked")
        var targetsCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(strategic).load(eq("2026/2027"), targetsCaptor.capture());
        assertThat((List<SquadTargetResponse>) targetsCaptor.getValue())
                .extracting(SquadTargetResponse::playerId).containsExactly(1L);
    }

    private DynamicBudgetResponse budget() {
        return new DynamicBudgetResponse(25, 500, 7, 500, 493,
                Map.of("P", 0, "D", 0, "C", 0, "A", 0),
                Map.of("P", 3, "D", 8, "C", 8, "A", 6),
                Map.of("P", 60, "D", 100, "C", 250, "A", 590), 0);
    }

    private SquadTargetResponse target(Long id) {
        return new SquadTargetResponse(id, "Player", "Club", "D", bd(80), bd(70), bd(70),
                null, null, null, null, null, bd(75), "STARTER", bd(80),
                Map.of("B51_PRICE", EvidenceStatus.AVAILABLE), 20, 12, 22, 493,
                "TARGET", List.of());
    }

    private BigDecimal bd(int value) { return BigDecimal.valueOf(value).setScale(2); }
}
