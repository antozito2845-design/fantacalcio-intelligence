package it.fantacalciointelligence.backend.liveauction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record LiveAuctionDecisionResponse(
        LiveAuctionAction action,
        Long currentPlayerId,
        String currentPlayerName,
        String currentRole,
        Integer currentPrice,
        int proposedBid,
        int evaluatedBid,
        @Deprecated
        int currentBid,
        Integer b51RecommendedPrice,
        Integer targetPrice,
        Integer contextualMaxPrice,
        LiveAuctionPriceZone priceZone,
        int remainingBudget,
        int budgetAfterBid,
        BudgetSummary budget,
        FitSummary fit,
        StrategicSummary strategicSignals,
        CalendarSummary calendar,
        boolean availabilityComplete,
        boolean alternativeRecommendationAllowed,
        AlternativeSummary alternative,
        BigDecimal confidence,
        LiveAuctionEvidenceStatus evidenceStatus,
        List<String> reasons,
        List<String> warnings,
        List<String> missingInputs
) {
    @Deprecated
    public LiveAuctionDecisionResponse(LiveAuctionAction action, Long currentPlayerId,
            String currentPlayerName, String currentRole, int currentBid,
            Integer b51RecommendedPrice, Integer targetPrice, Integer contextualMaxPrice,
            LiveAuctionPriceZone priceZone, int remainingBudget, int budgetAfterBid,
            BudgetSummary budget, FitSummary fit, StrategicSummary strategicSignals,
            CalendarSummary calendar, AlternativeSummary alternative, BigDecimal confidence,
            LiveAuctionEvidenceStatus evidenceStatus, List<String> reasons,
            List<String> warnings, List<String> missingInputs) {
        this(action, currentPlayerId, currentPlayerName, currentRole, null,
                currentBid, currentBid, currentBid, b51RecommendedPrice, targetPrice,
                contextualMaxPrice, priceZone, remainingBudget, budgetAfterBid, budget,
                fit, strategicSignals, calendar, false, false, alternative, confidence,
                evidenceStatus, reasons, warnings, missingInputs);
    }
    public record BudgetSummary(int minimumCompletionReserve, int futureBudgetProtected,
            int currentRoleMinimumReserve, int currentRoleBudgetAvailable,
            int remainingSlotsTotal, boolean sustainable) { }

    public record FitSummary(BigDecimal score, String profileCategory,
            List<String> missingProfileNeeds, List<String> b54Reasons) { }

    public record StrategicSummary(Map<String, SignalSummary> signals,
            BigDecimal evidenceCompleteness, List<String> missingInputs) { }

    public record SignalSummary(BigDecimal score, String status,
            BigDecimal confidence, String category) { }

    public record CalendarSummary(BigDecimal score, String status,
            BigDecimal confidence, Integer fromMatchday, Integer horizon,
            Integer hardOverlapCount) { }

    public record AlternativeSummary(Long playerId, String playerName,
            Integer b51RecommendedPrice, Integer targetPrice,
            BigDecimal fitScore, String reason) { }
}
