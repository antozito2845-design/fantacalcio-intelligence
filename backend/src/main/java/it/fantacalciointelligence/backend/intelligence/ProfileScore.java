package it.fantacalciointelligence.backend.intelligence;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProfileScore(
        BigDecimal score,
        String confidence,
        Map<String, BigDecimal> components,
        List<String> reasons,
        List<String> limitations
) {
}
