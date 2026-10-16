package it.fantacalciointelligence.backend.lineup;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * B68 lineup scoring core.
 *
 * Principles:
 * - only real available signals are used;
 * - a missing signal is omitted, never converted to zero or a neutral 50;
 * - availability can make a player operationally unusable;
 * - fixture difficulty already contains home/away context, therefore no
 *   additional home bonus is applied here;
 * - confidence describes evidence quality and does not artificially
 *   increase or decrease the football score.
 */
@Component
public class B68LineupScorer {

    private static final double PERFORMANCE_WEIGHT = 0.45;
    private static final double STARTING_WEIGHT = 0.25;
    private static final double AVAILABILITY_WEIGHT = 0.20;
    private static final double FIXTURE_WEIGHT = 0.10;

    public ScoreResult score(Input input) {

        if (input == null) {
            throw new IllegalArgumentException(
                    "Input B68 obbligatorio"
            );
        }

        String role = normalizeRole(input.role());

        validatePercentage(
                input.fantasyAverageRolePercentile(),
                "fantasyAverageRolePercentile"
        );

        validatePercentage(
                input.usageRolePercentile(),
                "usageRolePercentile"
        );

        validatePercentage(
                input.goalRateRolePercentile(),
                "goalRateRolePercentile"
        );

        validatePercentage(
                input.assistRateRolePercentile(),
                "assistRateRolePercentile"
        );

        validatePercentage(
                decimal(input.startingProbability()),
                "startingProbability"
        );

        validatePercentage(
                decimal(input.availabilityProbability()),
                "availabilityProbability"
        );

        validatePercentage(
                decimal(input.fixtureDifficulty()),
                "fixtureDifficulty"
        );

        validatePercentage(
                decimal(input.dataConfidenceScore()),
                "dataConfidenceScore"
        );

        if (input.historySeasonsAvailable() < 0) {
            throw new IllegalArgumentException(
                    "historySeasonsAvailable non può essere negativo"
            );
        }

        String availabilityStatus =
                normalizeAvailabilityStatus(
                        input.availabilityStatus()
                );

        List<Double> performanceSignals =
                performanceSignals(
                        role,
                        input
                );

        Double performanceScore =
                medianOrNull(
                        performanceSignals
                );

        Double startingProbability =
                decimal(
                        input.startingProbability()
                );

        Double availabilityProbability =
                decimal(
                        input.availabilityProbability()
                );

        Double fixtureDifficulty =
                decimal(
                        input.fixtureDifficulty()
                );

        Double fixtureScore =
                fixtureDifficulty == null
                        ? null
                        : 100.0 - fixtureDifficulty;

        boolean hardUnavailable =
                isHardUnavailable(
                        availabilityStatus
                );

        int domainSignalCount = 0;

        if (performanceScore != null) {
            domainSignalCount++;
        }

        if (startingProbability != null) {
            domainSignalCount++;
        }

        if (availabilityProbability != null) {
            domainSignalCount++;
        }

        if (fixtureScore != null) {
            domainSignalCount++;
        }

        BigDecimal confidence =
                confidence(
                        performanceScore,
                        input.dataConfidenceScore(),
                        startingProbability,
                        availabilityProbability,
                        fixtureScore
                );

        String evidenceTier =
                evidenceTier(
                        performanceSignals.size(),
                        input.dataConfidenceScore(),
                        startingProbability,
                        availabilityProbability,
                        fixtureScore,
                        domainSignalCount
                );

        if (hardUnavailable) {
            return new ScoreResult(
                    BigDecimal.ZERO.setScale(2),
                    roundNullable(performanceScore),
                    roundNullable(fixtureScore),
                    roundNullable(startingProbability),
                    roundNullable(availabilityProbability),
                    performanceSignals.size(),
                    domainSignalCount,
                    evidenceTier,
                    confidence,
                    false,
                    exclusionReason(availabilityStatus)
            );
        }

        Double selectionScore =
                weightedAvailableScore(
                        performanceScore,
                        startingProbability,
                        availabilityProbability,
                        fixtureScore
                );

        return new ScoreResult(
                roundNullable(selectionScore),
                roundNullable(performanceScore),
                roundNullable(fixtureScore),
                roundNullable(startingProbability),
                roundNullable(availabilityProbability),
                performanceSignals.size(),
                domainSignalCount,
                evidenceTier,
                confidence,
                selectionScore != null,
                selectionScore == null
                        ? "Nessun segnale reale disponibile"
                        : null
        );
    }

    private List<Double> performanceSignals(
            String role,
            Input input
    ) {

        List<Double> signals =
                new ArrayList<>();

        addIfPresent(
                signals,
                input.fantasyAverageRolePercentile()
        );

        addIfPresent(
                signals,
                input.usageRolePercentile()
        );

        /*
         * For goalkeepers B66 does not expose role-normalized
         * goalkeeper-specific goal/assist performance indicators.
         * Therefore only fantasy average and usage are admitted.
         */
        if (!"P".equals(role)) {

            addIfPresent(
                    signals,
                    input.goalRateRolePercentile()
            );

            addIfPresent(
                    signals,
                    input.assistRateRolePercentile()
            );
        }

        return signals;
    }

    private Double weightedAvailableScore(
            Double performanceScore,
            Double startingProbability,
            Double availabilityProbability,
            Double fixtureScore
    ) {

        double weightedTotal = 0;
        double availableWeight = 0;

        if (performanceScore != null) {
            weightedTotal +=
                    performanceScore
                            * PERFORMANCE_WEIGHT;

            availableWeight +=
                    PERFORMANCE_WEIGHT;
        }

        if (startingProbability != null) {
            weightedTotal +=
                    startingProbability
                            * STARTING_WEIGHT;

            availableWeight +=
                    STARTING_WEIGHT;
        }

        if (availabilityProbability != null) {
            weightedTotal +=
                    availabilityProbability
                            * AVAILABILITY_WEIGHT;

            availableWeight +=
                    AVAILABILITY_WEIGHT;
        }

        if (fixtureScore != null) {
            weightedTotal +=
                    fixtureScore
                            * FIXTURE_WEIGHT;

            availableWeight +=
                    FIXTURE_WEIGHT;
        }

        if (availableWeight == 0) {
            return null;
        }

        return weightedTotal / availableWeight;
    }

    private BigDecimal confidence(
            Double performanceScore,
            BigDecimal dataConfidenceScore,
            Double startingProbability,
            Double availabilityProbability,
            Double fixtureScore
    ) {

        double value = 0;

        if (performanceScore != null) {
            Double performanceConfidence =
                    decimal(
                            dataConfidenceScore
                    );

            if (performanceConfidence != null) {
                value +=
                        performanceConfidence
                                * PERFORMANCE_WEIGHT;
            }
        }

        if (startingProbability != null) {
            value += 100 * STARTING_WEIGHT;
        }

        if (availabilityProbability != null) {
            value += 100 * AVAILABILITY_WEIGHT;
        }

        if (fixtureScore != null) {
            value += 100 * FIXTURE_WEIGHT;
        }

        return round(value);
    }

    private String evidenceTier(
            int performanceSignalCount,
            BigDecimal dataConfidenceScore,
            Double startingProbability,
            Double availabilityProbability,
            Double fixtureScore,
            int domainSignalCount
    ) {

        double historicalConfidence =
                dataConfidenceScore == null
                        ? 0
                        : dataConfidenceScore
                                .doubleValue();

        if (
                historicalConfidence >= 80
                && performanceSignalCount >= 3
                && startingProbability != null
                && availabilityProbability != null
                && fixtureScore != null
        ) {
            return "HIGH";
        }

        if (
                historicalConfidence >= 70
                && performanceSignalCount >= 1
                && fixtureScore != null
                && (
                        startingProbability != null
                        || availabilityProbability != null
                )
        ) {
            return "MEDIUM";
        }

        if (domainSignalCount >= 2) {
            return "LOW";
        }

        if (domainSignalCount == 1) {
            return "MINIMAL";
        }

        return "NO_EVIDENCE";
    }

    private boolean isHardUnavailable(
            String status
    ) {

        return "INJURED".equals(status)
                || "SUSPENDED".equals(status)
                || "UNAVAILABLE".equals(status);
    }

    private String exclusionReason(
            String status
    ) {

        return switch (status) {

            case "INJURED" ->
                    "Giocatore escluso: infortunato";

            case "SUSPENDED" ->
                    "Giocatore escluso: squalificato";

            case "UNAVAILABLE" ->
                    "Giocatore escluso: indisponibile";

            default ->
                    "Giocatore non utilizzabile";
        };
    }

    private String normalizeAvailabilityStatus(
            String value
    ) {

        if (
                value == null
                || value.isBlank()
        ) {
            return "UNKNOWN";
        }

        return value
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeRole(
            String role
    ) {

        if (role == null) {
            throw new IllegalArgumentException(
                    "Ruolo B68 obbligatorio"
            );
        }

        String normalized =
                role.trim()
                        .toUpperCase(Locale.ROOT);

        if (
                !"P".equals(normalized)
                && !"D".equals(normalized)
                && !"C".equals(normalized)
                && !"A".equals(normalized)
        ) {
            throw new IllegalArgumentException(
                    "Ruolo B68 non valido: "
                            + role
            );
        }

        return normalized;
    }

    private void validatePercentage(
            Double value,
            String field
    ) {

        if (value == null) {
            return;
        }

        if (
                !Double.isFinite(value)
                || value < 0
                || value > 100
        ) {
            throw new IllegalArgumentException(
                    field
                            + " deve essere compreso tra 0 e 100"
            );
        }
    }

    private void addIfPresent(
            List<Double> values,
            Double value
    ) {

        if (value != null) {
            values.add(value);
        }
    }

    private Double medianOrNull(
            List<Double> values
    ) {

        if (values.isEmpty()) {
            return null;
        }

        List<Double> ordered =
                values.stream()
                        .sorted(
                                Comparator.naturalOrder()
                        )
                        .toList();

        int size = ordered.size();

        if (size % 2 == 1) {
            return ordered.get(
                    size / 2
            );
        }

        return (
                ordered.get(
                        size / 2 - 1
                )
                + ordered.get(
                        size / 2
                )
        ) / 2.0;
    }

    private Double decimal(
            BigDecimal value
    ) {

        return value == null
                ? null
                : value.doubleValue();
    }

    private BigDecimal roundNullable(
            Double value
    ) {

        return value == null
                ? null
                : round(value);
    }

    private BigDecimal round(
            double value
    ) {

        return BigDecimal
                .valueOf(value)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                );
    }

    public record Input(
            String role,
            Double fantasyAverageRolePercentile,
            Double usageRolePercentile,
            Double goalRateRolePercentile,
            Double assistRateRolePercentile,
            BigDecimal startingProbability,
            BigDecimal availabilityProbability,
            String availabilityStatus,
            BigDecimal fixtureDifficulty,
            BigDecimal dataConfidenceScore,
            int historySeasonsAvailable
    ) {
    }

    public record ScoreResult(
            BigDecimal selectionScore,
            BigDecimal performanceScore,
            BigDecimal fixtureScore,
            BigDecimal startingProbability,
            BigDecimal availabilityProbability,
            int performanceSignalCount,
            int domainSignalCount,
            String evidenceTier,
            BigDecimal confidence,
            boolean actionable,
            String exclusionReason
    ) {
    }
}