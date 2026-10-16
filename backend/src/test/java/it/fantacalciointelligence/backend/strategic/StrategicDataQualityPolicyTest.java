package it.fantacalciointelligence.backend.strategic;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StrategicDataQualityPolicyTest {
    private final StrategicDataQualityPolicy policy = new StrategicDataQualityPolicy();

    @Test void genericSetPieceFlagNeverBecomesPrimary() {
        assertThat(policy.hierarchyFromGenericFlag(true)).isEqualTo("UNKNOWN");
        assertThat(policy.hierarchyFromGenericFlag(false)).isEqualTo("UNKNOWN");
        assertThat(policy.hierarchyFromGenericFlag(null)).isNull();
    }

    @Test void per90RequiresPositiveMinutesAndRealValue() {
        assertThat(policy.per90(BigDecimal.TEN, 900)).isEqualByComparingTo("1.0000");
        assertThat(policy.per90(BigDecimal.TEN, 0)).isNull();
        assertThat(policy.per90(BigDecimal.TEN, null)).isNull();
        assertThat(policy.per90(null, 900)).isNull();
    }

    @Test void percentageDoesNotInventZeroForMissingOrZeroTotal() {
        assertThat(policy.percentage(3, 4)).isEqualByComparingTo("75.00");
        assertThat(policy.percentage(null, 4)).isNull();
        assertThat(policy.percentage(0, 0)).isNull();
    }
}
