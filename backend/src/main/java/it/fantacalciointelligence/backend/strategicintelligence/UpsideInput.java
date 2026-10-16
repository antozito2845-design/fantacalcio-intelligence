package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.util.List;

public record UpsideInput(BigDecimal potentialScore, BigDecimal reliabilityScore,
        BigDecimal riskScore, BigDecimal starterProbability, BigDecimal marketGap,
        StrategicSignal roleAdvantage, StrategicSignal bonusOpportunity,
        boolean activeRegistration, List<String> sources) {}
