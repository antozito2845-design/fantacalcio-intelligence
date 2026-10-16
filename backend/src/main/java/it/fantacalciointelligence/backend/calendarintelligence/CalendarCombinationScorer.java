package it.fantacalciointelligence.backend.calendarintelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CalendarCombinationScorer {
    public CalendarCombinationResult evaluate(List<ClubFixtureProfile> profiles, int from, int horizon) {
        validate(profiles, from, horizon);
        List<ClubFixtureProfile> ordered = profiles.stream().sorted(Comparator.comparing(ClubFixtureProfile::clubId)).toList();
        int to = Math.min(38, from + horizon - 1);
        int window = to - from + 1, favorable = 0, hardOverlap = 0, complementary = 0, known = 0;
        List<Integer> missing = new ArrayList<>();
        Map<Integer, Long> preferred = new LinkedHashMap<>();
        List<Double> dailyCoverage = new ArrayList<>();
        for (int day = from; day <= to; day++) {
            List<ClubFixtureDay> fixtures = new ArrayList<>();
            for (ClubFixtureProfile profile : ordered) {
                ClubFixtureDay fixture = profile.matchdays().get(day);
                if (fixture != null && fixture.usable()) fixtures.add(fixture);
            }
            if (fixtures.size() != ordered.size()) { missing.add(day); continue; }
            known++;
            long easy = fixtures.stream().filter(f -> f.difficulty() == CalendarDifficulty.EASY).count();
            long hard = fixtures.stream().filter(f -> f.difficulty() == CalendarDifficulty.HARD).count();
            if (easy > 0) favorable++;
            if (hard == fixtures.size()) hardOverlap++;
            if (easy > 0 && hard > 0) complementary++;
            dailyCoverage.add(easy > 0 ? 1.0 : hard == fixtures.size() ? 0.0 : .5);
            if (easy == 1 && hard == fixtures.size() - 1) {
                preferred.put(day, fixtures.stream().filter(f -> f.difficulty() == CalendarDifficulty.EASY).findFirst().orElseThrow().clubId());
            }
        }
        double completeness = known * 100.0 / window;
        if (known < 2 || completeness < 60) return new CalendarCombinationResult(ids(ordered), null,
                CalendarCombinationStatus.INSUFFICIENT_EVIDENCE, bd(completeness), favorable, hardOverlap,
                known == 0 ? null : bd(hardOverlap * 100.0 / known), complementary, missing, from, to,
                Map.of(), List.of("Insufficient usable fixture window"));
        double favorableRate = favorable * 100.0 / known;
        double hardRate = hardOverlap * 100.0 / known;
        double complementRate = complementary * 100.0 / known;
        double streakRisk = longestZeroStreak(dailyCoverage) * 100.0 / known;
        double redundancyPenalty = ordered.size() == 3 ? redundantEasyDays(ordered, from, to) * 4.0 / known : 0;
        double score = favorableRate * .45 + complementRate * .25 + (100 - hardRate) * .25
                + (100 - streakRisk) * .05 - redundancyPenalty;
        CalendarCombinationStatus status = completeness == 100 ? CalendarCombinationStatus.AVAILABLE : CalendarCombinationStatus.PARTIAL;
        return new CalendarCombinationResult(ids(ordered), bd(score), status, bd(completeness), favorable,
                hardOverlap, bd(hardRate), complementary, missing, from, to, Map.copyOf(preferred),
                List.of("Favorable coverage=" + favorable, "Hard overlap=" + hardOverlap,
                        "Complementary matchdays=" + complementary, "Completeness=" + bd(completeness)));
    }

    private void validate(List<ClubFixtureProfile> profiles, int from, int horizon) {
        if (profiles == null || profiles.size() < 2 || profiles.size() > 3) throw new IllegalArgumentException("Pair or triple required");
        if (from < 1 || from > 38 || horizon < 1 || horizon > 10) throw new IllegalArgumentException("Invalid fixture window");
        if (profiles.stream().map(ClubFixtureProfile::clubId).distinct().count() != profiles.size()) throw new IllegalArgumentException("Duplicate club profile");
    }
    private int longestZeroStreak(List<Double> values) { int max=0,current=0; for(double v:values){if(v==0){current++;max=Math.max(max,current);}else current=0;} return max; }
    private int redundantEasyDays(List<ClubFixtureProfile> profiles, int from, int to) {
        int count=0; for(int d=from;d<=to;d++){int easy=0; for(var p:profiles){var f=p.matchdays().get(d);if(f!=null&&f.usable()&&f.difficulty()==CalendarDifficulty.EASY)easy++;}if(easy==profiles.size())count++;}return count;
    }
    private List<Long> ids(List<ClubFixtureProfile> profiles) { return profiles.stream().map(ClubFixtureProfile::clubId).toList(); }
    private BigDecimal bd(double value) { return BigDecimal.valueOf(Math.max(0,Math.min(100,value))).setScale(2, RoundingMode.HALF_UP); }
}
