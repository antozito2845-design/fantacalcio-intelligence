package it.fantacalciointelligence.backend.strategic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrategicCsvValueMapperTest {
    private final StrategicCsvValueMapper mapper = new StrategicCsvValueMapper(new RealPositionNormalizer());

    @Test void preservesMissingValuesAsNullAndRawPositionSeparately() {
        Map<String, String> row = new HashMap<>();
        row.put("real_position", " right wing-back ");
        var mapped = mapper.map(row);
        assertThat(mapped.realPositionRaw()).isEqualTo("right wing-back");
        assertThat(mapped.realPositionNormalized()).isEqualTo(RealPosition.RWB);
        assertThat(mapped.shots()).isNull();
        assertThat(mapped.expectedGoals()).isNull();
        assertThat(mapped.headedGoals()).isNull();
    }

    @Test void mapsSupportedMetricsWithoutEstimatingOthers() {
        var mapped = mapper.map(Map.of("shots", "12", "expected_goals", "2,4", "aerial_duels", "9"));
        assertThat(mapped.shots()).isEqualTo(12);
        assertThat(mapped.expectedGoals()).isEqualByComparingTo("2.4");
        assertThat(mapped.aerialDuels()).isEqualTo(9);
        assertThat(mapped.aerialDuelsWon()).isNull();
    }

    @Test void rejectsNegativeSourceMetrics() {
        assertThatThrownBy(() -> mapper.map(Map.of("headed_goals", "-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
