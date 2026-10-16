package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.util.List;

public record StrategicSignal(
        BigDecimal score,
        StrategicSignalStatus status,
        BigDecimal confidence,
        String category,
        List<String> evidenceSummary,
        List<String> sourceReferences,
        List<String> reasons,
        List<String> missingInputs
) {}
