package it.fantacalciointelligence.backend.strategicintelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AerialThreatService {
    public StrategicSignal calculate(AerialThreatInput in) {
        List<String> missing = new ArrayList<>();
        boolean offensiveHeading = in.headedShots() != null || in.headedGoals() != null || in.headedExpectedGoals() != null;
        boolean presence = in.offensiveBoxTouches() != null || in.setPieceBoxPresence() != null || in.cornerTargetFrequency() != null;
        boolean general = in.aerialDuels() != null || in.aerialDuelsWon() != null;
        if (!offensiveHeading) missing.add("offensiveHeadingEvidence");
        if (!presence) missing.add("offensiveBoxOrSetPiecePresence");
        if (!offensiveHeading) {
            StrategicSignalStatus status = general ? StrategicSignalStatus.PARTIAL : StrategicSignalStatus.INSUFFICIENT_EVIDENCE;
            return new StrategicSignal(null, status, general ? bd(20) : BigDecimal.ZERO, "INSUFFICIENT_EVIDENCE",
                    general ? List.of("GENERAL_AERIAL_EVIDENCE_ONLY") : List.of(), safe(in.sources()),
                    general ? List.of("Generic aerial duels cannot establish offensive threat") : List.of(), missing);
        }
        if (in.minutes() == null || in.minutes() <= 0) missing.add("positiveMinutes");
        if (!presence || in.minutes() == null || in.minutes() <= 0)
            return new StrategicSignal(null, StrategicSignalStatus.PARTIAL, bd(35), "PARTIAL_OFFENSIVE_HEADING_EVIDENCE",
                    List.of("OFFENSIVE_HEADING_EVIDENCE"), safe(in.sources()), conflict(in.sourceConflict()), missing);
        double per90Shots = in.headedShots() == null ? 0 : in.headedShots() * 90.0 / in.minutes();
        double per90Xg = in.headedExpectedGoals() == null ? 0 : in.headedExpectedGoals().doubleValue() * 90 / in.minutes();
        double score = Math.min(100, per90Shots * 25 + per90Xg * 180 + nz(in.headedGoals()) * 2
                + value(in.setPieceBoxPresence()) * 10 + value(in.cornerTargetFrequency()) * 10
                + nz(in.offensiveBoxTouches()) * .05);
        double confidence = in.evidenceConfidence() == null ? 50 : in.evidenceConfidence().doubleValue();
        if (in.sourceConflict()) confidence *= .65;
        return new StrategicSignal(bd(score), StrategicSignalStatus.AVAILABLE, bd(confidence),
                score >= 60 ? "STRONG_OFFENSIVE_AERIAL_THREAT" : score >= 30 ? "MODERATE_OFFENSIVE_AERIAL_THREAT" : "LIMITED_OFFENSIVE_AERIAL_THREAT",
                List.of("OFFENSIVE_HEADING_EVIDENCE", "BOX_OR_SET_PIECE_PRESENCE"), safe(in.sources()), conflict(in.sourceConflict()), missing);
    }
    private int nz(Integer v) { return v == null ? 0 : v; }
    private double value(BigDecimal v) { return v == null ? 0 : v.doubleValue(); }
    private List<String> safe(List<String> v) { return v == null ? List.of() : List.copyOf(v); }
    private List<String> conflict(boolean v) { return v ? List.of("SOURCE_CONFLICT_SURFACED") : List.of(); }
    private BigDecimal bd(double v) { return BigDecimal.valueOf(Math.max(0, Math.min(100, v))).setScale(2, RoundingMode.HALF_UP); }
}
