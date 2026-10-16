package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.util.List;

public record AerialThreatInput(Integer headedShots, Integer headedGoals,
        BigDecimal headedExpectedGoals, Integer offensiveBoxTouches,
        BigDecimal setPieceBoxPresence, BigDecimal cornerTargetFrequency,
        Integer aerialDuels, Integer aerialDuelsWon, Integer minutes,
        BigDecimal evidenceConfidence, List<String> sources, boolean sourceConflict) {}
