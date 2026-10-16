package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.util.List;
import it.fantacalciointelligence.backend.strategic.RealPosition;

public record RoleAdvantageInput(String fantasyRole, RealPosition realPosition,
        BigDecimal positionConfidence, Integer minutes, Integer starts,
        StrategicSignal bonusOpportunity, List<String> sources, boolean sourceConflict) {}
