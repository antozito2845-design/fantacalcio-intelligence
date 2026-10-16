package it.fantacalciointelligence.backend.intelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class PlayerIntelligenceProfileCalculator {

    public static final String MODEL_VERSION = "1.0";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public CalculatedProfile calculate(SeasonData current, SeasonData previous) {
        BigDecimal completeness = completeness(current);
        String confidence = confidence(completeness);
        return new CalculatedProfile(
                completeness,
                confidence,
                starter(current, confidence),
                reliability(current, confidence),
                bonus(current, confidence),
                malus(current, confidence),
                unavailable("Servono voti partita-per-partita; le medie stagionali non misurano la varianza"),
                trend(current, previous, confidence)
        );
    }

    private ProfileScore starter(SeasonData d, String confidence) {
        if (d.appearances() == null || d.appearances() == 0
                || d.starts() == null || d.minutes() == null) {
            return unavailable("Presenze, titolarità e minuti sono necessari");
        }
        BigDecimal starts = ratio(d.starts(), d.appearances(), 100);
        BigDecimal minutesShare = clamp(ratio(d.minutes(), d.appearances() * 90, 100));
        BigDecimal score = weighted(starts, 70, minutesShare, 30);
        return score(score, confidence,
                Map.of("startPercentage", starts, "minutesShare", minutesShare),
                List.of(d.starts() + " partenze su " + d.appearances() + " presenze",
                        d.minutes() + " minuti giocati"));
    }

    private ProfileScore reliability(SeasonData d, String confidence) {
        if (d.appearances() == null && d.minutes() == null) {
            return unavailable("Presenze o minuti sono necessari");
        }
        BigDecimal appearances = d.appearances() == null ? null
                : clamp(ratio(d.appearances(), 38, 100));
        BigDecimal minutes = d.minutes() == null ? null
                : clamp(ratio(d.minutes(), 3420, 100));
        BigDecimal score = averageWeighted(appearances, 60, minutes, 40);
        Map<String, BigDecimal> components = new LinkedHashMap<>();
        if (appearances != null) components.put("appearanceCoverage", appearances);
        if (minutes != null) components.put("minutesCoverage", minutes);
        return score(score, confidence, components,
                List.of("Copertura stagionale basata su presenze e minuti disponibili"));
    }

    private ProfileScore bonus(SeasonData d, String confidence) {
        if (d.minutes() == null || d.minutes() == 0
                || (d.goals() == null && d.assists() == null)) {
            return unavailable("Minuti e almeno una metrica offensiva sono necessari");
        }
        double goalBenchmark = switch (d.role()) {
            case "P" -> 0.04; case "D" -> 0.12; case "C" -> 0.25; default -> 0.50;
        };
        double assistBenchmark = switch (d.role()) {
            case "P" -> 0.02; case "D" -> 0.12; case "C" -> 0.25; default -> 0.20;
        };
        BigDecimal goalsPer90 = d.goals() == null ? null : per90(d.goals(), d.minutes());
        BigDecimal assistsPer90 = d.assists() == null ? null : per90(d.assists(), d.minutes());
        BigDecimal goalComponent = goalsPer90 == null ? null
                : clamp(goalsPer90.multiply(BigDecimal.valueOf(100 / goalBenchmark)));
        BigDecimal assistComponent = assistsPer90 == null ? null
                : clamp(assistsPer90.multiply(BigDecimal.valueOf(100 / assistBenchmark)));
        BigDecimal result = averageWeighted(goalComponent, 65, assistComponent, 35);
        Map<String, BigDecimal> components = new LinkedHashMap<>();
        if (goalsPer90 != null) components.put("goalsPer90", goalsPer90);
        if (assistsPer90 != null) components.put("assistsPer90", assistsPer90);
        return score(result, confidence, components,
                List.of("Soglie offensive normalizzate per il ruolo " + d.role()));
    }

    private ProfileScore malus(SeasonData d, String confidence) {
        if (d.appearances() == null || d.appearances() == 0
                || (d.yellowCards() == null && d.redCards() == null && d.penaltiesMissed() == null)) {
            return unavailable("Presenze e almeno una metrica disciplinare sono necessari");
        }
        BigDecimal yellow = d.yellowCards() == null ? null
                : clamp(ratio(d.yellowCards(), d.appearances(), 100).divide(BigDecimal.valueOf(.30), 2, RoundingMode.HALF_UP));
        BigDecimal red = d.redCards() == null ? null
                : clamp(ratio(d.redCards(), d.appearances(), 100).divide(BigDecimal.valueOf(.08), 2, RoundingMode.HALF_UP));
        BigDecimal missed = d.penaltiesMissed() == null ? null
                : clamp(ratio(d.penaltiesMissed(), d.appearances(), 100).divide(BigDecimal.valueOf(.05), 2, RoundingMode.HALF_UP));
        BigDecimal result = averageWeighted(yellow, 45, red, 35, missed, 20);
        Map<String, BigDecimal> components = new LinkedHashMap<>();
        if (yellow != null) components.put("yellowCardRisk", yellow);
        if (red != null) components.put("redCardRisk", red);
        if (missed != null) components.put("penaltyMissRisk", missed);
        return score(result, confidence, components, List.of("Rischio per presenza, non totale grezzo"));
    }

    private ProfileScore trend(SeasonData current, SeasonData previous, String confidence) {
        if (previous == null) return unavailable("Una sola stagione disponibile: nessuna penalizzazione applicata");
        BigDecimal currentMetric = trendMetric(current);
        BigDecimal previousMetric = trendMetric(previous);
        if (currentMetric == null || previousMetric == null) {
            return unavailable("Metriche comparabili insufficienti tra le stagioni");
        }
        BigDecimal delta = currentMetric.subtract(previousMetric);
        BigDecimal result = clamp(BigDecimal.valueOf(50).add(delta.multiply(BigDecimal.valueOf(25))));
        return score(result, confidence, Map.of("seasonDelta", delta.setScale(2, RoundingMode.HALF_UP)),
                List.of("Confronto con la stagione " + previous.season()));
    }

    private BigDecimal trendMetric(SeasonData d) {
        if (d.fantasyAverage() != null) return d.fantasyAverage();
        if (d.minutes() == null || d.minutes() == 0) return null;
        int bonus = (d.goals() == null ? 0 : d.goals()) + (d.assists() == null ? 0 : d.assists());
        return per90(bonus, d.minutes());
    }

    private BigDecimal completeness(SeasonData d) {
        Object[] fields = {d.appearances(), d.starts(), d.minutes(), d.goals(), d.assists(),
                d.yellowCards(), d.redCards(), d.penaltiesMissed(), d.averageRating(), d.fantasyAverage()};
        int present = 0;
        for (Object field : fields) if (field != null) present++;
        return BigDecimal.valueOf(present * 10L).setScale(2);
    }

    private String confidence(BigDecimal completeness) {
        if (completeness.compareTo(BigDecimal.valueOf(80)) >= 0) return "HIGH";
        if (completeness.compareTo(BigDecimal.valueOf(50)) >= 0) return "MEDIUM";
        return "LOW";
    }

    private ProfileScore unavailable(String limitation) {
        return new ProfileScore(null, "UNAVAILABLE", Map.of(), List.of(), List.of(limitation));
    }

    private ProfileScore score(BigDecimal value, String confidence, Map<String, BigDecimal> components,
            List<String> reasons) {
        return new ProfileScore(clamp(value), confidence, Map.copyOf(components), List.copyOf(reasons), List.of());
    }

    private BigDecimal per90(int value, int minutes) { return ratio(value * 90, minutes, 1); }
    private BigDecimal ratio(int numerator, int denominator, int multiplier) {
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(multiplier))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
    private BigDecimal weighted(BigDecimal a, int aw, BigDecimal b, int bw) {
        return a.multiply(BigDecimal.valueOf(aw)).add(b.multiply(BigDecimal.valueOf(bw)))
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
    }
    private BigDecimal averageWeighted(BigDecimal a, int aw, BigDecimal b, int bw) {
        return averageWeighted(new BigDecimal[] {a, b}, new int[] {aw, bw});
    }
    private BigDecimal averageWeighted(BigDecimal a, int aw, BigDecimal b, int bw,
            BigDecimal c, int cw) {
        return averageWeighted(new BigDecimal[] {a, b, c}, new int[] {aw, bw, cw});
    }
    private BigDecimal averageWeighted(BigDecimal[] values, int[] weightValues) {
        BigDecimal total = BigDecimal.ZERO;
        int weights = 0;
        for (int i = 0; i < values.length; i++) {
            BigDecimal value = values[i];
            int weight = weightValues[i];
            if (value != null) { total = total.add(value.multiply(BigDecimal.valueOf(weight))); weights += weight; }
        }
        return weights == 0 ? null : total.divide(BigDecimal.valueOf(weights), 2, RoundingMode.HALF_UP);
    }
    private BigDecimal clamp(BigDecimal value) {
        if (value == null) return null;
        return value.max(BigDecimal.ZERO).min(ONE_HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    public record SeasonData(String season, String role, Integer appearances, Integer starts, Integer minutes,
            Integer goals, Integer assists, Integer yellowCards, Integer redCards, Integer penaltiesMissed,
            BigDecimal averageRating, BigDecimal fantasyAverage) {}
    public record CalculatedProfile(BigDecimal completeness, String confidence, ProfileScore starter,
            ProfileScore reliability, ProfileScore bonus, ProfileScore malus, ProfileScore consistency,
            ProfileScore trend) {}
}
