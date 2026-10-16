package it.fantacalciointelligence.backend.strategic;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class StrategicDataQualityPolicy {
    public String hierarchyFromGenericFlag(Boolean flag) {
        return flag == null ? null : "UNKNOWN";
    }

    public BigDecimal per90(BigDecimal value, Integer minutes) {
        if (value == null || minutes == null || minutes <= 0) return null;
        return value.multiply(BigDecimal.valueOf(90))
                .divide(BigDecimal.valueOf(minutes), 4, RoundingMode.HALF_UP);
    }

    public BigDecimal percentage(Integer won, Integer total) {
        if (won == null || total == null || total <= 0) return null;
        return BigDecimal.valueOf(won).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
