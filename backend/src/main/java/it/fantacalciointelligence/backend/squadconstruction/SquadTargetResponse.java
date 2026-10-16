package it.fantacalciointelligence.backend.squadconstruction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record SquadTargetResponse(
        Long playerId,
        String playerName,
        String club,
        String role,
        BigDecimal qualityScore,
        BigDecimal valueScore,
        BigDecimal fitScore,
        BigDecimal bonusOpportunityScore,
        BigDecimal roleAdvantageScore,
        BigDecimal calendarFitScore,
        BigDecimal aerialThreatScore,
        BigDecimal upsideScore,
        BigDecimal auctionPriorityScore,
        String profileCategory,
        BigDecimal confidence,
        Map<String, EvidenceStatus> evidenceFlags,
        int b51RecommendedPrice,
        int averageSlotBudget,
        int targetPrice,
        int contextualMaxPrice,
        String recommendation,
        List<String> reasons
) {
}
