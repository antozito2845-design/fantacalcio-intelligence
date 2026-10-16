package it.fantacalciointelligence.backend.calendarintelligence;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class B69RoleMatchupScorer {

    private static final BigDecimal ONE_HUNDRED =
            new BigDecimal("100");

    public MatchupResult score(Input input) {

        if (input == null) {
            throw new IllegalArgumentException(
                    "Matchup input is required"
            );
        }

        String role =
                normalizeRole(
                        input.role()
                );

        validateRating(
                "ownAttackingRating",
                input.ownAttackingRating()
        );

        validateRating(
                "ownDefensiveRating",
                input.ownDefensiveRating()
        );

        validateRating(
                "opponentOverallRating",
                input.opponentOverallRating()
        );

        validateRating(
                "opponentAttackingRating",
                input.opponentAttackingRating()
        );

        validateRating(
                "opponentDefensiveRating",
                input.opponentDefensiveRating()
        );

        validateRating(
                "opponentHomeRating",
                input.opponentHomeRating()
        );

        validateRating(
                "opponentAwayRating",
                input.opponentAwayRating()
        );

        BigDecimal opponentVenueRating =
                input.home()
                        ? input.opponentAwayRating()
                        : input.opponentHomeRating();

        List<Signal> signals =
                switch (role) {

                    case "P" ->
                            goalkeeperSignals(
                                    input,
                                    opponentVenueRating
                            );

                    case "D" ->
                            defenderSignals(
                                    input,
                                    opponentVenueRating
                            );

                    case "C" ->
                            midfielderSignals(
                                    input,
                                    opponentVenueRating
                            );

                    case "A" ->
                            forwardSignals(
                                    input,
                                    opponentVenueRating
                            );

                    default ->
                            throw new IllegalStateException(
                                    "Unsupported role "
                                            + role
                            );
                };

        List<Signal> available =
                signals.stream()
                        .filter(signal ->
                                signal.value() != null
                        )
                        .toList();

        int maximumSignalCount =
                signals.size();

        if (available.isEmpty()) {

            return new MatchupResult(
                    role,
                    null,
                    CalendarDifficulty.UNKNOWN,
                    "NO_EVIDENCE",
                    BigDecimal.ZERO
                            .setScale(2),
                    0,
                    maximumSignalCount,
                    opponentVenueRating,
                    input.home()
            );
        }

        BigDecimal availableWeight =
                available.stream()
                        .map(Signal::weight)
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal weightedTotal =
                available.stream()
                        .map(signal ->
                                signal.value()
                                        .multiply(
                                                signal.weight()
                                        )
                        )
                        .reduce(
                                BigDecimal.ZERO,
                                BigDecimal::add
                        );

        BigDecimal difficultyScore =
                weightedTotal
                        .divide(
                                availableWeight,
                                8,
                                RoundingMode.HALF_UP
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        BigDecimal evidenceCoverage =
                availableWeight
                        .multiply(
                                ONE_HUNDRED
                        )
                        .setScale(
                                2,
                                RoundingMode.HALF_UP
                        );

        return new MatchupResult(
                role,
                difficultyScore,
                CalendarDifficulty.fromScore(
                        difficultyScore
                ),
                evidenceTier(
                        evidenceCoverage
                ),
                evidenceCoverage,
                available.size(),
                maximumSignalCount,
                opponentVenueRating,
                input.home()
        );
    }

    private List<Signal> goalkeeperSignals(
            Input input,
            BigDecimal opponentVenueRating
    ) {

        return List.of(
                signal(
                        "opponentAttack",
                        "0.50",
                        input.opponentAttackingRating()
                ),
                signal(
                        "opponentVenue",
                        "0.25",
                        opponentVenueRating
                ),
                signal(
                        "opponentOverall",
                        "0.15",
                        input.opponentOverallRating()
                ),
                signal(
                        "ownDefensiveWeakness",
                        "0.10",
                        inverse(
                                input.ownDefensiveRating()
                        )
                )
        );
    }

    private List<Signal> defenderSignals(
            Input input,
            BigDecimal opponentVenueRating
    ) {

        return List.of(
                signal(
                        "opponentAttack",
                        "0.40",
                        input.opponentAttackingRating()
                ),
                signal(
                        "opponentOverall",
                        "0.20",
                        input.opponentOverallRating()
                ),
                signal(
                        "opponentVenue",
                        "0.20",
                        opponentVenueRating
                ),
                signal(
                        "ownDefensiveWeakness",
                        "0.20",
                        inverse(
                                input.ownDefensiveRating()
                        )
                )
        );
    }

    private List<Signal> midfielderSignals(
            Input input,
            BigDecimal opponentVenueRating
    ) {

        return List.of(
                signal(
                        "opponentOverall",
                        "0.30",
                        input.opponentOverallRating()
                ),
                signal(
                        "opponentDefense",
                        "0.25",
                        input.opponentDefensiveRating()
                ),
                signal(
                        "opponentAttack",
                        "0.15",
                        input.opponentAttackingRating()
                ),
                signal(
                        "opponentVenue",
                        "0.15",
                        opponentVenueRating
                ),
                signal(
                        "ownAttackingWeakness",
                        "0.15",
                        inverse(
                                input.ownAttackingRating()
                        )
                )
        );
    }

    private List<Signal> forwardSignals(
            Input input,
            BigDecimal opponentVenueRating
    ) {

        return List.of(
                signal(
                        "opponentDefense",
                        "0.45",
                        input.opponentDefensiveRating()
                ),
                signal(
                        "opponentOverall",
                        "0.20",
                        input.opponentOverallRating()
                ),
                signal(
                        "opponentVenue",
                        "0.15",
                        opponentVenueRating
                ),
                signal(
                        "ownAttackingWeakness",
                        "0.20",
                        inverse(
                                input.ownAttackingRating()
                        )
                )
        );
    }

    private Signal signal(
            String name,
            String weight,
            BigDecimal value
    ) {

        return new Signal(
                name,
                new BigDecimal(weight),
                value
        );
    }

    private BigDecimal inverse(
            BigDecimal rating
    ) {

        if (rating == null) {
            return null;
        }

        return ONE_HUNDRED.subtract(
                rating
        );
    }

    private String normalizeRole(
            String role
    ) {

        if (role == null) {
            throw new IllegalArgumentException(
                    "Fantasy role is required"
            );
        }

        String normalized =
                role.trim()
                        .toUpperCase();

        if (
                !List.of(
                        "P",
                        "D",
                        "C",
                        "A"
                ).contains(normalized)
        ) {
            throw new IllegalArgumentException(
                    "Invalid fantasy role: "
                            + role
            );
        }

        return normalized;
    }

    private void validateRating(
            String name,
            BigDecimal rating
    ) {

        if (rating == null) {
            return;
        }

        if (
                rating.compareTo(
                        BigDecimal.ZERO
                ) < 0
                || rating.compareTo(
                        ONE_HUNDRED
                ) > 0
        ) {
            throw new IllegalArgumentException(
                    name
                            + " must be between 0 and 100"
            );
        }
    }

    private String evidenceTier(
            BigDecimal coverage
    ) {

        if (
                coverage.compareTo(
                        new BigDecimal("90")
                ) >= 0
        ) {
            return "HIGH";
        }

        if (
                coverage.compareTo(
                        new BigDecimal("70")
                ) >= 0
        ) {
            return "MEDIUM";
        }

        if (
                coverage.compareTo(
                        new BigDecimal("40")
                ) >= 0
        ) {
            return "LOW";
        }

        return "MINIMAL";
    }

    public record Input(
            String role,
            boolean home,
            BigDecimal ownAttackingRating,
            BigDecimal ownDefensiveRating,
            BigDecimal opponentOverallRating,
            BigDecimal opponentAttackingRating,
            BigDecimal opponentDefensiveRating,
            BigDecimal opponentHomeRating,
            BigDecimal opponentAwayRating
    ) {
    }

    public record MatchupResult(
            String role,
            BigDecimal difficultyScore,
            CalendarDifficulty difficulty,
            String evidenceTier,
            BigDecimal evidenceCoverage,
            int signalCount,
            int maximumSignalCount,
            BigDecimal opponentVenueRating,
            boolean home
    ) {
    }

    private record Signal(
            String name,
            BigDecimal weight,
            BigDecimal value
    ) {
    }
}