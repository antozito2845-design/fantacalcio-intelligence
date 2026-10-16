package it.fantacalciointelligence.backend.intelligence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.intelligence.PlayerIntelligenceProfileCalculator.CalculatedProfile;
import it.fantacalciointelligence.backend.intelligence.PlayerIntelligenceProfileCalculator.SeasonData;

class PlayerIntelligenceProfileCalculatorTest {

    private final PlayerIntelligenceProfileCalculator calculator = new PlayerIntelligenceProfileCalculator();

    @Test
    void calculatesCompleteExplainableDeterministicProfileInRange() {
        SeasonData current = data("2026/2027", "C", 30, 24, 2300, 8, 7, 5, 0, 1, "6.25", "6.90");
        SeasonData previous = data("2025/2026", "C", 32, 20, 2100, 5, 5, 7, 1, 0, "6.10", "6.45");

        CalculatedProfile first = calculator.calculate(current, previous);
        CalculatedProfile second = calculator.calculate(current, previous);

        assertThat(first).isEqualTo(second);
        assertThat(first.completeness()).isEqualByComparingTo("100.00");
        assertThat(first.confidence()).isEqualTo("HIGH");
        assertThat(first.starter().score()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(first.reliability().score()).isBetween(BigDecimal.ZERO, BigDecimal.valueOf(100));
        assertThat(first.bonus().components()).containsKeys("goalsPer90", "assistsPer90");
        assertThat(first.bonus().reasons()).isNotEmpty();
        assertThat(first.malus().components()).containsKeys("yellowCardRisk", "redCardRisk", "penaltyMissRisk");
        assertThat(first.trend().score()).isGreaterThan(BigDecimal.valueOf(50));
        assertThat(first.consistency().score()).isNull();
        assertThat(first.consistency().limitations()).singleElement().asString().contains("partita-per-partita");
    }

    @Test
    void missingDataIsUnavailableRatherThanLowScore() {
        SeasonData sparse = data("2026/2027", "A", null, null, null, null, null,
                null, null, null, null, null);
        CalculatedProfile result = calculator.calculate(sparse, null);

        assertThat(result.completeness()).isZero();
        assertThat(result.confidence()).isEqualTo("LOW");
        assertThat(result.starter().score()).isNull();
        assertThat(result.bonus().score()).isNull();
        assertThat(result.trend().score()).isNull();
        assertThat(result.trend().confidence()).isEqualTo("UNAVAILABLE");
    }

    @Test
    void bonusPotentialIsRoleAware() {
        SeasonData defender = data("2026/2027", "D", 30, 30, 2700, 5, 3, 3, 0, 0, null, null);
        SeasonData attacker = data("2026/2027", "A", 30, 30, 2700, 5, 3, 3, 0, 0, null, null);

        assertThat(calculator.calculate(defender, null).bonus().score())
                .isGreaterThan(calculator.calculate(attacker, null).bonus().score());
    }

    @Test
    void zeroMinutesDoesNotDivideByZero() {
        SeasonData data = data("2026/2027", "P", 0, 0, 0, 0, 0, 0, 0, 0, null, null);
        CalculatedProfile result = calculator.calculate(data, null);
        assertThat(result.starter().score()).isNull();
        assertThat(result.bonus().score()).isNull();
        assertThat(result.malus().score()).isNull();
    }

    private SeasonData data(String season, String role, Integer appearances, Integer starts, Integer minutes,
            Integer goals, Integer assists, Integer yellow, Integer red, Integer missed,
            String average, String fantasyAverage) {
        return new SeasonData(season, role, appearances, starts, minutes, goals, assists, yellow, red, missed,
                average == null ? null : new BigDecimal(average),
                fantasyAverage == null ? null : new BigDecimal(fantasyAverage));
    }
}
