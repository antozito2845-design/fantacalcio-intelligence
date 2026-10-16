package it.fantacalciointelligence.backend.development;

import java.math.BigDecimal;

public record PlayerDevelopmentAssessmentResponse(
        Long playerId,
        String playerName,
        String targetSeason,
        String olderSeason,
        String latestSeason,
        String trend,
        BigDecimal growthScore,
        BigDecimal declineRiskScore,
        BigDecimal confidenceScore,
        BigDecimal appearancesChange,
        BigDecimal startsChange,
        BigDecimal minutesChange,
        BigDecimal fantasyAverageChange,
        BigDecimal goalsChange,
        BigDecimal assistsChange,
        BigDecimal expectedGoalsChange,
        BigDecimal expectedAssistsChange,
        String analysisSummary
) {
}