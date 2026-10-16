package it.fantacalciointelligence.backend.squadconstruction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class SquadTargetScorer {

    public String classifyForRoster(
            Candidate candidate,
            int effectivePrice,
            int averageSlotBudget
    ) {
        Candidate priced = new Candidate(
                candidate.playerId(),
                candidate.playerName(),
                candidate.club(),
                candidate.role(),
                candidate.qualityScore(),
                candidate.b50Confidence(),
                Math.max(1, effectivePrice),
                candidate.potentialScore(),
                candidate.starterProbability(),
                candidate.penaltyTaker(),
                candidate.setPieceTaker(),
                candidate.cornerTaker(),
                candidate.freeKickTaker(),
                candidate.attackingInvolvement(),
                candidate.realPosition()
        );
        double value = clamp(
                priced.qualityScore()
                        * Math.min(
                                1.25,
                                (double) averageSlotBudget
                                        / priced.b51Price()
                        )
        );
        Score upside = upside(priced, value);
        return category(
                priced,
                priced.qualityScore(),
                value,
                upside.value()
        );
    }

    public SquadTargetResponse score(Candidate candidate, DynamicBudgetResponse budget, String phaseRole) {
        return score(
                candidate,
                budget,
                phaseRole,
                RosterProfileContext.empty(
                        budget.slotsRemainingByRole().get(candidate.role()),
                        false
                )
        );
    }

    public SquadTargetResponse score(
            Candidate candidate,
            DynamicBudgetResponse budget,
            String phaseRole,
            RosterProfileContext roster
    ) {
        double quality = candidate.qualityScore();
        int roleSlots = Math.max(1, budget.slotsRemainingByRole().get(candidate.role()));
        int averageSlotBudget = Math.max(
                1,
                budget.remainingBudgetTargets().get(candidate.role())
                        / roleSlots
        );
        int contextualMax = Math.max(1, Math.min(budget.contextualMaximumBid(),
                budget.currentRoleBudgetAvailable()));

        // Affordability uses the role average, before targetPrice exists.
        // This keeps VALUE free from circular dependency on the individual target.
        double priceRatio = (double) averageSlotBudget
                / Math.max(1, candidate.b51Price());
        double value = clamp(quality * Math.min(1.25, priceRatio));

        Score bonus = bonus(candidate);
        Score advantage = roleAdvantage(candidate);
        Score upside = upside(candidate, value);
        String category = category(
                candidate,
                quality,
                value,
                upside.value()
        );
        double fit = rosterFit(
                category,
                candidate,
                averageSlotBudget,
                roster
        );
        int target = individualTargetPrice(
                candidate,
                quality,
                fit,
                category,
                averageSlotBudget,
                contextualMax,
                roster
        );
        BigDecimal aerial = null; // No aerial/header evidence exists in the audited schema.
        BigDecimal calendar = null; // Pairing engine remains authoritative; no per-player fallback is invented.

        double priority = clamp(quality * .35 + value * .35 + fit * .30);
        if (candidate.b51Price() > contextualMax) priority = clamp(priority - 20);
        double confidence = averagePresent(candidate.b50Confidence(), bonus.confidence(),
                advantage.confidence(), upside.confidence());

        Map<String, EvidenceStatus> evidence = new LinkedHashMap<>();
        evidence.put("B50_QUALITY", EvidenceStatus.AVAILABLE);
        evidence.put("B51_PRICE", EvidenceStatus.AVAILABLE);
        evidence.put("BONUS_OPPORTUNITY", bonus.status());
        evidence.put("ROLE_ADVANTAGE", advantage.status());
        evidence.put("CALENDAR_FIT", EvidenceStatus.NOT_AVAILABLE);
        evidence.put("AERIAL_THREAT", EvidenceStatus.NOT_AVAILABLE);
        evidence.put("UPSIDE", upside.status());

        List<String> reasons = new ArrayList<>();
        reasons.add("Qualità B50 " + round(quality) + "/100");
        reasons.add("Prezzo B51 " + candidate.b51Price()
                + " crediti; budget medio slot " + averageSlotBudget
                + "; target individuale " + target);
        reasons.add("Fit reparto " + round(fit)
                + "/100 per profilo " + category);
        if (bonus.value() != null) reasons.add("Opportunità bonus basata sui segnali tattici disponibili");
        if (advantage.value() != null) reasons.add("Posizione reale confrontata separatamente col ruolo Fantacalcio");
        if (aerial == null) reasons.add("Minaccia aerea non disponibile: metriche aeree assenti");

        String recommendation = candidate.b51Price() > contextualMax ? "EVITARE SOVRAPREZZO"
                : priority >= 75 ? "TARGET PRIORITARIO"
                : value >= 70 ? "VALUE TARGET" : "VALUTARE AL PREZZO TARGET";
        return new SquadTargetResponse(candidate.playerId(), candidate.playerName(), candidate.club(), candidate.role(),
                bd(quality), bd(value), bd(fit), nullable(bonus.value()), nullable(advantage.value()), calendar,
                aerial, nullable(upside.value()), bd(priority), category, bd(confidence), Map.copyOf(evidence),
                candidate.b51Price(), averageSlotBudget, target,
                contextualMax, recommendation, List.copyOf(reasons));
    }

    private double rosterFit(
            String category,
            Candidate candidate,
            int averageSlotBudget,
            RosterProfileContext roster
    ) {
        if (roster.slotsRemaining() <= 0) {
            return 0;
        }

        boolean premiumCovered =
                roster.covered("PREMIUM")
                        + roster.covered("SEMI_TOP") > 0;
        boolean bonusCovered =
                roster.covered("BONUS_SPECIALIST") > 0;
        boolean valueDepthCovered =
                roster.covered("VALUE_PICK")
                        + roster.covered("STARTER")
                        + roster.covered("LOW_COST")
                        >= Math.max(1, roster.slotsRemaining() / 2);

        double fit = 45;

        if (!premiumCovered
                && ("PREMIUM".equals(category)
                        || "SEMI_TOP".equals(category))) {
            fit += roster.defenseModifier()
                    && "D".equals(candidate.role())
                            ? 35
                            : 25;
        }
        else if (premiumCovered
                && "PREMIUM".equals(category)) {
            fit -= 20;
        }

        if (!bonusCovered
                && "BONUS_SPECIALIST".equals(category)) {
            fit += 20;
        }

        if (!valueDepthCovered
                && ("VALUE_PICK".equals(category)
                        || "STARTER".equals(category)
                        || "LOW_COST".equals(category))) {
            fit += 25;
        }

        if (roster.slotsRemaining() == 1) {
            if (("VALUE_PICK".equals(category)
                    || "LOW_COST".equals(category))
                    && candidate.b51Price() <= averageSlotBudget) {
                fit += 25;
            }
            if (("PREMIUM".equals(category)
                    || "SEMI_TOP".equals(category))
                    && candidate.b51Price() > averageSlotBudget) {
                fit -= 30;
            }
        }

        return clamp(fit);
    }

    private int individualTargetPrice(
            Candidate candidate,
            double quality,
            double fit,
            String category,
            int averageSlotBudget,
            int contextualMax,
            RosterProfileContext roster
    ) {
        // Transparent B54.1 adjustment around the immutable B51 price:
        // 80% base + up to 10% quality + up to 15% roster fit.
        double multiplier = 0.80
                + quality * 0.001
                + fit * 0.0015;

        boolean premiumNeeded =
                roster.covered("PREMIUM")
                        + roster.covered("SEMI_TOP") == 0;

        if (premiumNeeded
                && ("PREMIUM".equals(category)
                        || "SEMI_TOP".equals(category))) {
            multiplier += 0.15;
        }

        int target = (int) Math.round(
                candidate.b51Price() * multiplier
        );

        if (("VALUE_PICK".equals(category)
                || "LOW_COST".equals(category))
                && candidate.b51Price() <= averageSlotBudget) {
            target = Math.min(
                    target,
                    candidate.b51Price()
                            + Math.max(1, candidate.b51Price() / 10)
            );
        }

        return Math.max(1, Math.min(contextualMax, target));
    }

    private Score bonus(Candidate c) {
        List<Double> signals = new ArrayList<>();
        if (c.penaltyTaker() != null) signals.add(c.penaltyTaker() ? 100d : 0d);
        if (c.setPieceTaker() != null) signals.add(c.setPieceTaker() ? 85d : 0d);
        if (c.cornerTaker() != null) signals.add(c.cornerTaker() ? 75d : 0d);
        if (c.freeKickTaker() != null) signals.add(c.freeKickTaker() ? 80d : 0d);
        if (c.attackingInvolvement() != null) signals.add(c.attackingInvolvement());
        if (signals.isEmpty()) return Score.missing();
        return new Score(signals.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                signals.size() >= 3 ? EvidenceStatus.AVAILABLE : EvidenceStatus.PARTIAL,
                Math.min(100, signals.size() * 20));
    }

    private Score roleAdvantage(Candidate c) {
        if (c.realPosition() == null || c.realPosition().isBlank()) return Score.missing();
        String position = c.realPosition().toUpperCase();
        double score = 50;
        if ("D".equals(c.role()) && (position.contains("ALA") || position.contains("ESTERNO") || position.contains("CENTROCAM"))) score = 90;
        else if ("C".equals(c.role()) && (position.contains("TREQUART") || position.contains("ATTACC") || position.contains("ALA"))) score = 90;
        return new Score(score, EvidenceStatus.PARTIAL, 50);
    }

    private Score upside(Candidate c, double value) {
        List<Double> signals = new ArrayList<>();
        if (c.potentialScore() != null) signals.add(c.potentialScore());
        if (c.starterProbability() != null) signals.add(c.starterProbability());
        if (value >= 70) signals.add(value);
        if (signals.isEmpty()) return Score.missing();
        return new Score(signals.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                signals.size() >= 2 ? EvidenceStatus.PARTIAL : EvidenceStatus.PARTIAL,
                Math.min(75, signals.size() * 25));
    }

    private String category(Candidate c, double quality, double value, Double upside) {
        if (quality >= 90) return "PREMIUM";
        if (quality >= 80) return "SEMI_TOP";
        if (value >= 75) return "VALUE_PICK";
        if (upside != null && upside >= 75) return "UPSIDE_BET";
        if (Boolean.TRUE.equals(c.penaltyTaker()) || Boolean.TRUE.equals(c.setPieceTaker())) return "BONUS_SPECIALIST";
        if (c.b51Price() <= 3) return "LOW_COST";
        return "STARTER";
    }

    private double averagePresent(double... values) {
        return java.util.Arrays.stream(values).filter(v -> v >= 0).average().orElse(0);
    }
    private double clamp(double value) { return Math.max(0, Math.min(100, value)); }
    private BigDecimal bd(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal nullable(Double value) { return value == null ? null : bd(value); }
    private String round(double value) { return bd(value).toPlainString(); }

    public record Candidate(Long playerId, String playerName, String club, String role,
            double qualityScore, double b50Confidence, int b51Price, Double potentialScore,
            Double starterProbability, Boolean penaltyTaker, Boolean setPieceTaker,
            Boolean cornerTaker, Boolean freeKickTaker, Double attackingInvolvement,
            String realPosition) { }

    private record Score(Double value, EvidenceStatus status, double confidence) {
        static Score missing() { return new Score(null, EvidenceStatus.NOT_AVAILABLE, -1); }
    }
}
