package it.fantacalciointelligence.backend.liveauction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.squadconstruction.DynamicBudgetResponse;
import it.fantacalciointelligence.backend.squadconstruction.EvidenceStatus;
import it.fantacalciointelligence.backend.squadconstruction.SquadTargetResponse;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicIntelligenceProfile;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicSignal;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicSignalStatus;

class LiveAuctionDecisionEngineTest {
    private final LiveAuctionDecisionPolicy policy = new LiveAuctionDecisionPolicy();
    private final LiveAuctionDecisionEngine engine = new LiveAuctionDecisionEngine(
            new LiveAuctionBudgetGuard(), new LiveAuctionAlternativeService(policy), policy);

    @Test
    void deepValueWithStrongFitIsBuyNow() {
        var response = decide(20, target(1, 40, 30, 50, 80, 85), budget(100, 50, 5), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.ACQUISTA_ORA);
        assertThat(response.priceZone()).isEqualTo(LiveAuctionPriceZone.VALUE_ZONE);
    }

    @Test
    void valueZoneBelowTargetIsRaise() {
        var response = decide(28, target(1, 40, 30, 50, 60, 85), budget(100, 50, 5), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.RILANCIA);
    }

    @Test
    void oneCreditFromContextualMaximumIsStop() {
        var response = decide(49, target(1, 40, 30, 50, 80, 85), budget(100, 50, 5), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.FERMATI);
    }

    @Test
    void exactTargetBoundaryRemainsValueZone() {
        var response = decide(30, target(1, 40, 30, 50, 60, 85), budget(100, 50, 5), List.of());
        assertThat(response.priceZone()).isEqualTo(LiveAuctionPriceZone.VALUE_ZONE);
        assertThat(response.action()).isEqualTo(LiveAuctionAction.RILANCIA);
    }

    @Test
    void strictCautionInteriorIsObserveWithoutSupport() {
        var response = decide(35, target(1, 40, 30, 50, 60, 85), budget(100, 50, 5), List.of());
        assertThat(response.priceZone()).isEqualTo(LiveAuctionPriceZone.CAUTION_ZONE);
        assertThat(response.action()).isEqualTo(LiveAuctionAction.OSSERVA);
    }

    @Test
    void contextualMaximumHasPriorityOverStrongFit() {
        var response = decide(50, target(1, 40, 30, 50, 95, 95), budget(100, 50, 5), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.LASCIA);
    }

    @Test
    void aboveContextualMaximumIsLeave() {
        var response = decide(51, target(1, 40, 30, 50, 95, 95), budget(100, 60, 5), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.LASCIA);
        assertThat(response.priceZone()).isEqualTo(LiveAuctionPriceZone.STOP_ZONE);
    }

    @Test
    void completionReserveHasHighestPriority() {
        var response = decide(25, target(1, 40, 30, 90, 95, 95), budget(30, 90, 10), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.LASCIA);
        assertThat(response.budget().sustainable()).isFalse();
    }

    @Test
    void genuinelyDominantAlternativeIsPreferredDeterministically() {
        var current = target(1, 80, 70, 95, 60, 85);
        var worseId = target(3, 45, 50, 90, 75, 80);
        var betterId = target(2, 45, 50, 90, 75, 80);
        var response = decide(80, current, budget(150, 95, 5), true,
                List.of(evaluation(worseId), evaluation(betterId)));
        assertThat(response.action()).isEqualTo(LiveAuctionAction.ALTERNATIVA_MIGLIORE);
        assertThat(response.alternative().playerId()).isEqualTo(2L);
    }

    @Test
    void cheaperAlternativeWithWorseFitDoesNotDominate() {
        var current = target(1, 80, 70, 95, 75, 85);
        var cheapButWrong = target(2, 40, 45, 90, 40, 80);
        var response = decide(80, current, budget(150, 95, 5), List.of(evaluation(cheapButWrong)));
        assertThat(response.action()).isNotEqualTo(LiveAuctionAction.ALTERNATIVA_MIGLIORE);
    }

    @Test
    void incompleteAvailabilityNeverProducesBetterAlternative() {
        var current = target(1, 80, 70, 95, 60, 85);
        var dominant = target(2, 45, 50, 90, 75, 80);
        var response = decide(80, current, budget(150, 95, 5), false,
                List.of(evaluation(dominant)));
        assertThat(response.action()).isNotEqualTo(LiveAuctionAction.ALTERNATIVA_MIGLIORE);
        assertThat(response.alternativeRecommendationAllowed()).isFalse();
        assertThat(response.warnings()).contains("Disponibilita globale alternative non certificata");
    }

    @Test
    void currentPriceIsInformativeAndProposedBidDrivesStopAndBudgetAfterBid() {
        var current = target(1, 40, 30, 50, 80, 85);
        var budget = budget(100, 50, 5);
        var request = request(10, 50, current, budget, true);
        var response = engine.decide(request, budget, List.of(), evaluation(current), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.LASCIA);
        assertThat(response.currentPrice()).isEqualTo(10);
        assertThat(response.proposedBid()).isEqualTo(50);
        assertThat(response.evaluatedBid()).isEqualTo(50);
        assertThat(response.budgetAfterBid()).isEqualTo(50);
    }

    @Test
    void missingB56AndB57AreNotConvertedToZeroPenalty() {
        var response = decide(28, target(1, 40, 30, 50, 70, 85), budget(100, 50, 5), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.RILANCIA);
        assertThat(response.missingInputs()).contains("B56_STRATEGIC_PROFILE", "CURRENT_MATCHDAY_FOR_B57");
    }

    @Test
    void weakEvidenceInCautionZoneProducesInsufficientEvidence() {
        var response = decide(35, target(1, 40, 30, 50, 45, 10), budget(100, 50, 5), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void availableBonusCanSupportSelectiveRaiseInCautionZone() {
        var target = target(1, 40, 30, 50, 75, 85);
        var response = engine.decide(request(35, target, budget(100, 50, 5)), budget(100, 50, 5),
                List.of(), new LiveAuctionCandidateEvaluation(target, profile(available(80), missing(),
                        missing(), missing(), 25), null), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.RILANCIA);
    }

    @Test
    void partialAerialSignalIsNotTreatedAsFullSupport() {
        var target = target(1, 40, 30, 50, 75, 85);
        var response = engine.decide(request(35, target, budget(100, 50, 5)), budget(100, 50, 5),
                List.of(), new LiveAuctionCandidateEvaluation(target, profile(missing(), missing(),
                        partial(90), missing(), 25), null), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.OSSERVA);
    }

    @Test
    void highUpsideNeverOverridesStopZone() {
        var target = target(1, 40, 30, 50, 95, 95);
        var response = engine.decide(request(50, target, budget(100, 50, 5)), budget(100, 50, 5),
                List.of(), new LiveAuctionCandidateEvaluation(target, profile(missing(), missing(),
                        missing(), available(100), 25), null), List.of());
        assertThat(response.action()).isEqualTo(LiveAuctionAction.LASCIA);
    }

    private LiveAuctionDecisionResponse decide(int bid, SquadTargetResponse current,
            DynamicBudgetResponse budget, List<LiveAuctionCandidateEvaluation> alternatives) {
        return decide(bid, current, budget, false, alternatives);
    }

    private LiveAuctionDecisionResponse decide(int bid, SquadTargetResponse current,
            DynamicBudgetResponse budget, boolean availabilityComplete,
            List<LiveAuctionCandidateEvaluation> alternatives) {
        return engine.decide(request(null, bid, current, budget, availabilityComplete), budget,
                List.of("MANCA_PREMIUM_O_SEMI_TOP"),
                evaluation(current), alternatives);
    }

    private LiveAuctionDecisionRequest request(int bid, SquadTargetResponse current,
            DynamicBudgetResponse budget) {
        return request(null, bid, current, budget, false);
    }

    private LiveAuctionDecisionRequest request(Integer currentPrice, int bid,
            SquadTargetResponse current, DynamicBudgetResponse budget, boolean availabilityComplete) {
        int remainingBudget = budget.remainingBudgetTargets().values().stream().mapToInt(Integer::intValue).sum();
        return new LiveAuctionDecisionRequest("2026/2027", 8, remainingBudget, remainingBudget, false,
                "D", current.playerId(), currentPrice, bid, null, null, List.of(), null, null,
                10, List.of(), List.of(), availabilityComplete);
    }

    private LiveAuctionCandidateEvaluation evaluation(SquadTargetResponse target) {
        return new LiveAuctionCandidateEvaluation(target, null, null);
    }

    private DynamicBudgetResponse budget(int remainingBudget, int maximum, int remainingSlots) {
        return new DynamicBudgetResponse(remainingSlots, 10, Math.max(0, remainingSlots - 1),
                maximum, maximum, Map.of("P", 0, "D", 0, "C", 0, "A", 0),
                Map.of("P", 0, "D", remainingSlots, "C", 0, "A", 0),
                Map.of("P", 0, "D", remainingBudget, "C", 0, "A", 0), 0);
    }

    private SquadTargetResponse target(long id, int b51, int target, int maximum,
            int fit, int confidence) {
        return new SquadTargetResponse(id, "Player " + id, "Club", "D", bd(80), bd(70), bd(fit),
                null, null, null, null, null, bd(75), "STARTER", bd(confidence),
                Map.of("B51_PRICE", EvidenceStatus.AVAILABLE), b51, 10, target, maximum,
                "VALUTARE AL PREZZO TARGET", List.of("B54 FIT"));
    }

    private BigDecimal bd(int value) { return BigDecimal.valueOf(value).setScale(2); }

    private StrategicIntelligenceProfile profile(StrategicSignal bonus, StrategicSignal role,
            StrategicSignal aerial, StrategicSignal upside, int completeness) {
        return new StrategicIntelligenceProfile(1L, "2026/2027", "D", bonus, role, aerial, upside,
                bd(completeness), List.of(), List.of("TEST"), List.of());
    }

    private StrategicSignal available(int score) {
        return new StrategicSignal(bd(score), StrategicSignalStatus.AVAILABLE, bd(90), "HIGH",
                List.of(), List.of("TEST"), List.of(), List.of());
    }

    private StrategicSignal partial(int score) {
        return new StrategicSignal(bd(score), StrategicSignalStatus.PARTIAL, bd(40), "PARTIAL",
                List.of(), List.of("TEST"), List.of(), List.of());
    }

    private StrategicSignal missing() {
        return new StrategicSignal(null, StrategicSignalStatus.INSUFFICIENT_EVIDENCE, bd(0),
                "INSUFFICIENT_EVIDENCE", List.of(), List.of(), List.of(), List.of("metric"));
    }
}
