package it.fantacalciointelligence.backend.liveauction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.calendarintelligence.CalendarCombinationResult;
import it.fantacalciointelligence.backend.squadconstruction.DynamicBudgetResponse;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicIntelligenceProfile;
import it.fantacalciointelligence.backend.strategicintelligence.StrategicSignal;

@Component
public class LiveAuctionDecisionEngine {
    private final LiveAuctionBudgetGuard budgetGuard;
    private final LiveAuctionAlternativeService alternativeService;
    private final LiveAuctionDecisionPolicy policy;

    public LiveAuctionDecisionEngine(LiveAuctionBudgetGuard budgetGuard,
            LiveAuctionAlternativeService alternativeService,
            LiveAuctionDecisionPolicy policy) {
        this.budgetGuard = budgetGuard;
        this.alternativeService = alternativeService;
        this.policy = policy;
    }

    public LiveAuctionDecisionResponse decide(LiveAuctionDecisionRequest request,
            DynamicBudgetResponse budget, List<String> missingProfileNeeds,
            LiveAuctionCandidateEvaluation current,
            List<LiveAuctionCandidateEvaluation> alternatives) {
        int bid = request.proposedBid();
        var target = current.target();
        var safety = budgetGuard.evaluate(bid, request.remainingBudget(), budget);
        LiveAuctionPriceZone zone = zone(bid, target.targetPrice(), target.contextualMaxPrice());
        boolean alternativeAllowed = Boolean.TRUE.equals(request.availabilityComplete());
        Optional<LiveAuctionCandidateEvaluation> dominant = safety.sustainable() && alternativeAllowed
                ? alternativeService.dominantAlternative(current, bid, alternatives)
                : Optional.empty();

        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        LiveAuctionAction action;

        // Explicit precedence: budget safety, B54 max, roster/alternative, price,
        // strategic/calendar support. Evidence never overrides a stop condition.
        if (!safety.sustainable()) {
            action = LiveAuctionAction.LASCIA;
            reasons.add(safety.reason());
        } else if (bid >= target.contextualMaxPrice()) {
            action = LiveAuctionAction.LASCIA;
            reasons.add("CURRENT_BID_AT_OR_ABOVE_B54_CONTEXTUAL_MAX");
        } else if (bid >= Math.max(1, target.contextualMaxPrice()
                - LiveAuctionDecisionPolicy.CONTEXTUAL_MAX_STOP_DISTANCE)) {
            action = LiveAuctionAction.FERMATI;
            reasons.add("CURRENT_BID_ONE_CREDIT_FROM_B54_CONTEXTUAL_MAX");
        } else if (dominant.isPresent()) {
            action = LiveAuctionAction.ALTERNATIVA_MIGLIORE;
            reasons.add("REAL_AVAILABLE_ALTERNATIVE_DOMINATES_ON_FIT_AND_AFFORDABILITY");
        } else if (bid <= target.targetPrice()) {
            if (policy.immediateBuy(bid, target.targetPrice(), target.fitScore())) {
                action = LiveAuctionAction.ACQUISTA_ORA;
                reasons.add("DEEP_VALUE_ZONE_WITH_STRONG_B54_FIT");
            } else {
                action = LiveAuctionAction.RILANCIA;
                reasons.add("WITHIN_B54_TARGET_AND_BUDGET_SAFE");
            }
        } else if (policy.cautionFit(target.fitScore()) && strategicSupport(current.strategic())) {
            action = LiveAuctionAction.RILANCIA;
            reasons.add("SELECTIVE_CAUTION_ZONE_BID_SUPPORTED_BY_FIT_AND_B56");
        } else {
            action = LiveAuctionAction.OSSERVA;
            reasons.add("CAUTION_ZONE_WITHOUT_ENOUGH_SUPPORT_FOR_A_STRONG_CALL");
        }

        if (current.strategic() == null) missing.add("B56_STRATEGIC_PROFILE");
        else missing.addAll(current.strategic().missingCriticalInputs());
        if (request.currentMatchday() == null) missing.add("CURRENT_MATCHDAY_FOR_B57");
        else if (current.calendar() == null) missing.add("B57_CALENDAR_COMPLEMENT");
        if (current.strategic() == null) warnings.add("B56 evidence unavailable; it was not converted to zero");
        if (current.calendar() == null) warnings.add("B57 evidence unavailable; budget/FIT decision remains valid");
        if (!alternativeAllowed) warnings.add("Disponibilita globale alternative non certificata");

        BigDecimal confidence = confidence(current, request.currentMatchday() != null, !alternatives.isEmpty());
        LiveAuctionEvidenceStatus evidence = confidence.doubleValue()
                >= LiveAuctionDecisionPolicy.AVAILABLE_CONFIDENCE_THRESHOLD
                ? LiveAuctionEvidenceStatus.AVAILABLE
                : confidence.doubleValue() >= LiveAuctionDecisionPolicy.PARTIAL_CONFIDENCE_THRESHOLD
                        ? LiveAuctionEvidenceStatus.PARTIAL
                : LiveAuctionEvidenceStatus.INSUFFICIENT_EVIDENCE;
        if (action == LiveAuctionAction.OSSERVA && evidence == LiveAuctionEvidenceStatus.INSUFFICIENT_EVIDENCE) {
            action = LiveAuctionAction.INSUFFICIENT_EVIDENCE;
        }

        int remainingSlots = budget.slotsRemainingByRole().values().stream().mapToInt(Integer::intValue).sum();
        var budgetSummary = new LiveAuctionDecisionResponse.BudgetSummary(
                safety.minimumCompletionReserve(), budget.futureBudgetProtected(),
                budget.currentRoleMinimumReserve(), budget.currentRoleBudgetAvailable(),
                remainingSlots, safety.sustainable());
        var fit = new LiveAuctionDecisionResponse.FitSummary(target.fitScore(), target.profileCategory(),
                List.copyOf(missingProfileNeeds), target.reasons());

        return new LiveAuctionDecisionResponse(action, target.playerId(), target.playerName(), target.role(),
                request.currentPrice(), bid, bid, bid,
                target.b51RecommendedPrice(), target.targetPrice(), target.contextualMaxPrice(), zone,
                request.remainingBudget(), safety.budgetAfterBid(), budgetSummary, fit,
                strategic(current.strategic()), calendar(current.calendar(), request), alternativeAllowed,
                alternativeAllowed,
                alternative(dominant), confidence, evidence, List.copyOf(reasons),
                List.copyOf(warnings), missing.stream().distinct().toList());
    }

    public LiveAuctionPriceZone zone(int bid, int target, int maximum) {
        if (bid <= target) return LiveAuctionPriceZone.VALUE_ZONE;
        if (bid < maximum) return LiveAuctionPriceZone.CAUTION_ZONE;
        return LiveAuctionPriceZone.STOP_ZONE;
    }

    private boolean strategicSupport(StrategicIntelligenceProfile profile) {
        if (profile == null) return false;
        return List.of(profile.bonusOpportunity(), profile.roleAdvantage(), profile.aerialThreat())
                .stream().anyMatch(signal -> policy.strategicSupport(signal.score(), signal.status().name()));
    }

    private BigDecimal confidence(LiveAuctionCandidateEvaluation current,
            boolean matchdaySupplied, boolean alternativesAvailable) {
        BigDecimal b54 = current.target().confidence();
        BigDecimal b56 = current.strategic() == null ? BigDecimal.ZERO
                : current.strategic().evidenceCompleteness();
        BigDecimal b57 = current.calendar() == null ? BigDecimal.ZERO
                : current.calendar().confidence();
        return policy.confidence(b54, b56, b57, alternativesAvailable, matchdaySupplied)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private LiveAuctionDecisionResponse.StrategicSummary strategic(StrategicIntelligenceProfile profile) {
        if (profile == null) return new LiveAuctionDecisionResponse.StrategicSummary(Map.of(), bd(0),
                List.of("B56_STRATEGIC_PROFILE"));
        Map<String, LiveAuctionDecisionResponse.SignalSummary> signals = new LinkedHashMap<>();
        signals.put("BONUS_OPPORTUNITY", signal(profile.bonusOpportunity()));
        signals.put("ROLE_ADVANTAGE", signal(profile.roleAdvantage()));
        signals.put("AERIAL_THREAT", signal(profile.aerialThreat()));
        signals.put("UPSIDE", signal(profile.upside()));
        return new LiveAuctionDecisionResponse.StrategicSummary(Map.copyOf(signals),
                profile.evidenceCompleteness(), profile.missingCriticalInputs());
    }

    private LiveAuctionDecisionResponse.SignalSummary signal(StrategicSignal signal) {
        return new LiveAuctionDecisionResponse.SignalSummary(signal.score(), signal.status().name(),
                signal.confidence(), signal.category());
    }

    private LiveAuctionDecisionResponse.CalendarSummary calendar(CalendarCombinationResult calendar,
            LiveAuctionDecisionRequest request) {
        if (calendar == null) return new LiveAuctionDecisionResponse.CalendarSummary(null,
                "INSUFFICIENT_EVIDENCE", bd(0), request.currentMatchday(), request.calendarHorizon(), null);
        return new LiveAuctionDecisionResponse.CalendarSummary(calendar.calendarCombinationScore(),
                calendar.status().name(), calendar.confidence(), calendar.fromMatchday(),
                request.calendarHorizon(), calendar.hardOverlapCount());
    }

    private LiveAuctionDecisionResponse.AlternativeSummary alternative(
            Optional<LiveAuctionCandidateEvaluation> candidate) {
        return candidate.map(value -> new LiveAuctionDecisionResponse.AlternativeSummary(
                value.target().playerId(), value.target().playerName(), value.target().b51RecommendedPrice(),
                value.target().targetPrice(), value.target().fitScore(),
                "Fit non inferiore, prezzo B51 almeno "
                        + LiveAuctionDecisionPolicy.ALTERNATIVE_MINIMUM_SAVING
                        + " crediti sotto il proposed bid ed evidence comparabile"))
                .orElse(null);
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(Math.max(0, Math.min(100, value))).setScale(2, RoundingMode.HALF_UP);
    }
}
