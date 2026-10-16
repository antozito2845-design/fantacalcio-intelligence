package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RoleAdvantageService {
    private static final Set<String> D_ATTACKING = Set.of("RWB","LWB","RW","LW");
    private static final Set<String> C_ATTACKING = Set.of("AM","RAM","LAM","RW","LW","SS");

    public StrategicSignal calculate(RoleAdvantageInput in) {
        List<String> missing = new ArrayList<>();
        if (in.fantasyRole() == null) missing.add("fantasyRole");
        if (in.realPosition() == null || in.realPosition().name().equals("UNKNOWN")) missing.add("realPosition");
        if (!missing.isEmpty()) return insufficient(missing, in.sources(), in.sourceConflict());
        double score = 50;
        String position = in.realPosition().name();
        if ("D".equals(in.fantasyRole()) && D_ATTACKING.contains(position)) score += 28;
        else if ("D".equals(in.fantasyRole()) && "CB".equals(position)) score -= 5;
        else if ("C".equals(in.fantasyRole()) && C_ATTACKING.contains(position)) score += 23;
        else if ("A".equals(in.fantasyRole()) && Set.of("AM","CM","DM").contains(position)) score -= 18;
        if (in.bonusOpportunity() != null && in.bonusOpportunity().score() != null)
            score += (in.bonusOpportunity().score().doubleValue() - 50) * .18;
        double confidence = in.positionConfidence() == null ? 35 : in.positionConfidence().doubleValue();
        if (in.minutes() == null || in.minutes() <= 0) { confidence *= .7; missing.add("positiveMinutes"); }
        if (in.sourceConflict()) confidence *= .6;
        String category = score >= 72 ? "STRONG_ADVANTAGE" : score >= 58 ? "MODERATE_ADVANTAGE"
                : score >= 42 ? "NEUTRAL" : "POTENTIAL_DISADVANTAGE";
        return new StrategicSignal(bd(score), confidence < 50 ? StrategicSignalStatus.PARTIAL : StrategicSignalStatus.AVAILABLE,
                bd(confidence), category, List.of("fantasyRole=" + in.fantasyRole(), "realPosition=" + position),
                safe(in.sources()), conflictReasons(in.sourceConflict()), List.copyOf(missing));
    }
    private StrategicSignal insufficient(List<String> missing, List<String> sources, boolean conflict) {
        return new StrategicSignal(null, StrategicSignalStatus.INSUFFICIENT_EVIDENCE, BigDecimal.ZERO,
                "INSUFFICIENT_EVIDENCE", List.of(), safe(sources), conflictReasons(conflict), missing);
    }
    private List<String> conflictReasons(boolean conflict) { return conflict ? List.of("SOURCE_CONFLICT_SURFACED") : List.of(); }
    private List<String> safe(List<String> v) { return v == null ? List.of() : List.copyOf(v); }
    private BigDecimal bd(double v) { return BigDecimal.valueOf(Math.max(0, Math.min(100, v))).setScale(2, RoundingMode.HALF_UP); }
}
