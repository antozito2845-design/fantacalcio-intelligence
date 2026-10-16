package it.fantacalciointelligence.backend.liveauction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationResult;
import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationStatus;
import it.fantacalciointelligence.backend.squadconstruction.EvidenceStatus;
import it.fantacalciointelligence.backend.squadconstruction.SquadTargetResponse;

class LiveAuctionAlternativeServiceTest {
    private final LiveAuctionAlternativeService service = new LiveAuctionAlternativeService(
            new LiveAuctionDecisionPolicy());

    @Test
    void goalkeeperCalendarComplementarityCanOffsetSmallFitGap() {
        var current = evaluation(target(1, "P", 70), calendar(55, 2));
        var alternative = evaluation(target(2, "P", 66), calendar(75, 1));
        assertThat(service.dominantAlternative(current, 50, List.of(alternative)))
                .contains(alternative);
    }

    @Test
    void attackerWithWorseHardOverlapCannotDominate() {
        var current = evaluation(target(1, "A", 70), calendar(65, 1));
        var alternative = evaluation(target(2, "A", 75), calendar(70, 3));
        assertThat(service.dominantAlternative(current, 50, List.of(alternative))).isEmpty();
    }

    private LiveAuctionCandidateEvaluation evaluation(SquadTargetResponse target,
            CalendarCombinationResult calendar) {
        return new LiveAuctionCandidateEvaluation(target, null, calendar);
    }

    private SquadTargetResponse target(long id, String role, int fit) {
        return new SquadTargetResponse(id, "Player " + id, "Club", role, bd(80), bd(70), bd(fit),
                null, null, null, null, null, bd(75), "STARTER", bd(80),
                Map.of("B51_PRICE", EvidenceStatus.AVAILABLE), 30, 10, 35, 80,
                "TARGET", List.of());
    }

    private CalendarCombinationResult calendar(int score, int hardOverlap) {
        return new CalendarCombinationResult(List.of(1L, 2L), bd(score),
                CalendarCombinationStatus.AVAILABLE, bd(100), 5, hardOverlap,
                bd(hardOverlap * 10), 3, List.of(), 1, 5, Map.of(), List.of());
    }

    private BigDecimal bd(int value) { return BigDecimal.valueOf(value).setScale(2); }
}
