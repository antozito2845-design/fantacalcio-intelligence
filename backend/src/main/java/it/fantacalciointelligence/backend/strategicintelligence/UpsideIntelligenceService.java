package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UpsideIntelligenceService {
    public StrategicSignal calculate(UpsideInput in) {
        List<String> missing = new ArrayList<>();
        if (in.potentialScore() == null) missing.add("potentialScore");
        int confirmations = 0;
        if (in.starterProbability() != null) confirmations++;
        if (in.marketGap() != null) confirmations++;
        if (available(in.roleAdvantage())) confirmations++;
        if (available(in.bonusOpportunity())) confirmations++;
        if (in.potentialScore() == null || confirmations < 2 || !in.activeRegistration()) {
            if (confirmations < 2) missing.add("twoIndependentConfirmations");
            if (!in.activeRegistration()) missing.add("activeRegistration");
            return new StrategicSignal(null, StrategicSignalStatus.INSUFFICIENT_EVIDENCE, BigDecimal.ZERO,
                    "INSUFFICIENT_EVIDENCE", List.of(), safe(in.sources()), List.of(), missing);
        }
        double score = in.potentialScore().doubleValue() * .45
                + value(in.starterProbability()) * .20
                + neutral(in.marketGap()) * .15
                + signal(in.roleAdvantage()) * .10
                + signal(in.bonusOpportunity()) * .10;
        double confidence = in.reliabilityScore() == null ? 45 : in.reliabilityScore().doubleValue();
        if (in.riskScore() != null) { score -= in.riskScore().doubleValue() * .18; confidence -= in.riskScore().doubleValue() * .25; }
        String category = score >= 70 ? "HIGH_UPSIDE" : score >= 55 ? "MODERATE_UPSIDE" : score >= 35 ? "STABLE" : "LOW_UPSIDE";
        return new StrategicSignal(bd(score), confidence >= 55 ? StrategicSignalStatus.AVAILABLE : StrategicSignalStatus.PARTIAL,
                bd(confidence), category, List.of("POTENTIAL", "INDEPENDENT_CONFIRMATIONS=" + confirmations),
                safe(in.sources()), in.riskScore() == null ? List.of() : List.of("Risk reduces upside and confidence"), missing);
    }
    private boolean available(StrategicSignal s) { return s != null && s.score() != null; }
    private double signal(StrategicSignal s) { return available(s) ? s.score().doubleValue() : 50; }
    private double value(BigDecimal v) { return v == null ? 50 : v.doubleValue(); }
    private double neutral(BigDecimal v) { return v == null ? 50 : Math.max(0, Math.min(100, 50 + v.doubleValue())); }
    private List<String> safe(List<String> v) { return v == null ? List.of() : List.copyOf(v); }
    private BigDecimal bd(double v) { return BigDecimal.valueOf(Math.max(0, Math.min(100, v))).setScale(2, RoundingMode.HALF_UP); }
}
