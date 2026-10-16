package it.fantacalciointelligence.backend.strategic;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StrategicCsvValueMapper {
    private final RealPositionNormalizer positionNormalizer;

    public StrategicCsvValueMapper(RealPositionNormalizer positionNormalizer) {
        this.positionNormalizer = positionNormalizer;
    }

    public MappedValues map(Map<String, String> row) {
        String rawPosition = blankToNull(row.get("real_position"));
        return new MappedValues(rawPosition,
                rawPosition == null ? null : positionNormalizer.normalize(rawPosition),
                integer(row.get("shots")), integer(row.get("shots_on_target")),
                decimal(row.get("expected_goals")), decimal(row.get("expected_assists")),
                integer(row.get("aerial_duels")), integer(row.get("aerial_duels_won")),
                integer(row.get("headed_shots")), integer(row.get("headed_goals")),
                decimal(row.get("headed_expected_goals")));
    }

    private Integer integer(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        int result = Integer.parseInt(normalized);
        if (result < 0) throw new IllegalArgumentException("Strategic count cannot be negative");
        return result;
    }

    private BigDecimal decimal(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        BigDecimal result = new BigDecimal(normalized.replace(',', '.'));
        if (result.signum() < 0) throw new IllegalArgumentException("Strategic metric cannot be negative");
        return result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record MappedValues(String realPositionRaw, RealPosition realPositionNormalized,
            Integer shots, Integer shotsOnTarget, BigDecimal expectedGoals, BigDecimal expectedAssists,
            Integer aerialDuels, Integer aerialDuelsWon, Integer headedShots, Integer headedGoals,
            BigDecimal headedExpectedGoals) {}
}
