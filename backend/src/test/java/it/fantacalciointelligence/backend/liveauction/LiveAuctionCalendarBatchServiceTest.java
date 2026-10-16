package it.fantacalciointelligence.backend.liveauction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.calendarintelligence.CalendarCandidateService.CalendarPlayerCandidate;
import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationResult;
import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationScorer;
import it.fantacalciointelligence.backend.calendarintelligence.ClubFixtureProfile;
import it.fantacalciointelligence.backend.calendarintelligence.FixtureWindowService;
import it.fantacalciointelligence.backend.squadconstruction.BoughtPlayerRequest;
import it.fantacalciointelligence.backend.squadconstruction.EvidenceStatus;
import it.fantacalciointelligence.backend.squadconstruction.SquadTargetResponse;

class LiveAuctionCalendarBatchServiceTest {
    @Test
    void loadsOneFixtureWindowForAllAlternatives() {
        FixtureWindowService window = mock(FixtureWindowService.class);
        CalendarCombinationScorer scorer = mock(CalendarCombinationScorer.class);
        LiveAuctionCalendarBatchService service = new LiveAuctionCalendarBatchService(window, scorer);
        var profiles = Map.of(10L, mock(ClubFixtureProfile.class),
                20L, mock(ClubFixtureProfile.class), 30L, mock(ClubFixtureProfile.class));
        when(window.loadClubProfiles(eq("2026/2027"), any(), eq(5), eq(6))).thenReturn(profiles);
        when(scorer.evaluate(any(), eq(5), eq(6))).thenReturn(mock(CalendarCombinationResult.class));
        List<CalendarPlayerCandidate> active = List.of(
                new CalendarPlayerCandidate(1L, "Owned", 10L, "A", "P"),
                new CalendarPlayerCandidate(2L, "Current", 20L, "B", "P"),
                new CalendarPlayerCandidate(3L, "Alternative", 30L, "C", "P"));

        var result = service.evaluate("2026/2027", 5, 6, List.of(target(2L), target(3L)),
                active, List.of(new BoughtPlayerRequest(1L, "P", 5)), "P");

        assertThat(result).containsKeys(2L, 3L);
        verify(window, times(1)).loadClubProfiles(eq("2026/2027"), any(), eq(5), eq(6));
        verify(scorer, times(2)).evaluate(any(), eq(5), eq(6));
    }

    private SquadTargetResponse target(Long id) {
        return new SquadTargetResponse(id, "Player", "Club", "P", bd(80), bd(70), bd(70),
                null, null, null, null, null, bd(75), "STARTER", bd(80),
                Map.of("B51_PRICE", EvidenceStatus.AVAILABLE), 20, 12, 22, 100,
                "TARGET", List.of());
    }

    private BigDecimal bd(int value) { return BigDecimal.valueOf(value).setScale(2); }
}
