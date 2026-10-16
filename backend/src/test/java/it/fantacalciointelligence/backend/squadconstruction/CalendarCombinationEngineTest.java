package it.fantacalciointelligence.backend.squadconstruction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class CalendarCombinationEngineTest {
    private final CalendarCombinationEngine engine = new CalendarCombinationEngine();

    @Test
    void complementaryFixturesBeatSimultaneousDifficultFixtures() {
        var complementary = engine.evaluate(List.of(values(30, 80, 35), values(80, 30, 75)));
        var difficultTogether = engine.evaluate(List.of(values(80, 85, 75), values(75, 80, 90)));
        assertThat(complementary.score()).isGreaterThan(difficultTogether.score());
    }

    @Test
    void missingCalendarStaysNotAvailable() {
        var result = engine.evaluate(List.of());
        assertThat(result.score()).isNull();
        assertThat(result.evidence()).isEqualTo(EvidenceStatus.NOT_AVAILABLE);
    }

    private List<BigDecimal> values(int... values) {
        return java.util.Arrays.stream(values).mapToObj(BigDecimal::valueOf).toList();
    }
}
