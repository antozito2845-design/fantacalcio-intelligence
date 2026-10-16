package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.util.List;

public record StrategicIntelligenceProfile(Long playerId, String season, String fantasyRole,
        StrategicSignal bonusOpportunity, StrategicSignal roleAdvantage,
        StrategicSignal aerialThreat, StrategicSignal upside,
        BigDecimal evidenceCompleteness, List<String> missingCriticalInputs,
        List<String> sourceSummary, List<String> conflicts) {}
