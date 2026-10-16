package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BonusOpportunityService {
    public StrategicSignal calculate(BonusOpportunityInput in) {
        List<String> missing = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        int dimensions = 0;
        double score = 0;
        if (explicit(in.penaltyHierarchy())) { dimensions++; score += hierarchy(in.penaltyHierarchy(), 32, 18); reasons.add("PENALTY_ACCESS=" + in.penaltyHierarchy()); }
        else missing.add("penaltyHierarchy");
        if (explicit(in.directFreeKickHierarchy())) { dimensions++; score += hierarchy(in.directFreeKickHierarchy(), 14, 8); reasons.add("DIRECT_FREE_KICK_ACCESS=" + in.directFreeKickHierarchy()); }
        if (explicit(in.cornerHierarchy()) || explicit(in.indirectFreeKickHierarchy())) { dimensions++; score += 10; reasons.add("SET_PIECE_CREATION"); }
        if (in.expectedGoalsPer90() != null || in.shotsPer90() != null || in.touchesInBox() != null) {
            dimensions++; score += Math.min(24, value(in.expectedGoalsPer90()) * 30 + value(in.shotsPer90()) * 3 + nz(in.touchesInBox()) * .05); reasons.add("OPEN_PLAY_GOAL_THREAT");
        } else missing.add("openPlayGoalThreat");
        if (in.expectedAssistsPer90() != null || in.keyPasses() != null) {
            dimensions++; score += Math.min(20, value(in.expectedAssistsPer90()) * 35 + nz(in.keyPasses()) * .08); reasons.add("OPEN_PLAY_ASSIST_THREAT");
        } else missing.add("openPlayAssistThreat");
        if (in.minutes() == null || in.minutes() <= 0) missing.add("positiveMinutes");
        if (dimensions < 2 || (in.minutes() == null || in.minutes() <= 0) && !explicit(in.penaltyHierarchy()))
            return unavailable(missing, in.sources(), in.sourceConflict());
        double confidence = in.evidenceConfidence() == null ? 40 : in.evidenceConfidence().doubleValue();
        if (in.sourceConflict()) confidence *= .65;
        if (dimensions == 2) confidence *= .75;
        return signal(score, confidence, dimensions >= 3 ? StrategicSignalStatus.AVAILABLE : StrategicSignalStatus.PARTIAL,
                score >= 65 ? "HIGH" : score >= 35 ? "MODERATE" : "LIMITED", reasons, missing, in.sources(), in.sourceConflict());
    }

    private StrategicSignal unavailable(List<String> missing, List<String> sources, boolean conflict) {
        return new StrategicSignal(null, StrategicSignalStatus.INSUFFICIENT_EVIDENCE, BigDecimal.ZERO, "INSUFFICIENT_EVIDENCE",
                List.of(), safe(sources), conflict ? List.of("SOURCE_CONFLICT_SURFACED") : List.of(), missing);
    }
    private boolean explicit(String v) { return "PRIMARY".equals(v) || "SECONDARY".equals(v); }
    private double hierarchy(String v, double primary, double secondary) { return "PRIMARY".equals(v) ? primary : secondary; }
    private double value(BigDecimal v) { return v == null ? 0 : v.doubleValue(); }
    private int nz(Integer v) { return v == null ? 0 : v; }
    private List<String> safe(List<String> v) { return v == null ? List.of() : List.copyOf(v); }
    private StrategicSignal signal(double score, double confidence, StrategicSignalStatus status, String category,
            List<String> reasons, List<String> missing, List<String> sources, boolean conflict) {
        List<String> finalReasons = new ArrayList<>(reasons); if (conflict) finalReasons.add("SOURCE_CONFLICT_SURFACED");
        return new StrategicSignal(bd(Math.min(100, score)), status, bd(Math.max(0, Math.min(100, confidence))), category,
                List.copyOf(reasons), safe(sources), finalReasons, List.copyOf(missing));
    }
    private BigDecimal bd(double v) { return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP); }
}
