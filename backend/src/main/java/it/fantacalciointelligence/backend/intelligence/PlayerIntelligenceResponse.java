package it.fantacalciointelligence.backend.intelligence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PlayerIntelligenceResponse(
        Long playerId,
        String playerName,
        String club,
        String season,
        String fantasyRole,
        String realPosition,
        String tacticalRole,

        BigDecimal projectedFantasyAverage,
        Integer projectedAppearances,
        Integer projectedStarts,
        Integer projectedGoals,
        Integer projectedAssists,
        BigDecimal reliabilityScore,
        BigDecimal potentialScore,
        BigDecimal riskScore,
        BigDecimal valueScore,
        BigDecimal starterProbability,
        Boolean penaltyTaker,
        Boolean setPieceTaker,

        String developmentTrend,
        BigDecimal growthScore,
        BigDecimal declineRiskScore,
        BigDecimal developmentConfidence,
        String developmentSummary,

        boolean foreignScoutingAvailable,
        String previousClub,
        String previousLeague,
        String previousCountryCode,
        BigDecimal adaptationScore,
        String scoutingRecommendation,
        String scoutingSummary,

        Integer availabilityMatchday,
        String availabilityStatus,
        BigDecimal availabilityProbability,
        BigDecimal startingProbability,
        String injuryDescription,
        LocalDate expectedReturnDate,

        int minimumPrice,
        int recommendedPrice,
        int maximumPrice,
        BigDecimal budgetPercentage,
        BigDecimal auctionScore,

        List<String> strengths,
        List<String> risks,
        String finalRecommendation,
        String intelligenceSummary
) {
}