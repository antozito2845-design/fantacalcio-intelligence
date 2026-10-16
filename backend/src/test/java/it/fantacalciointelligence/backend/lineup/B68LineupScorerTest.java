package it.fantacalciointelligence.backend.lineup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class B68LineupScorerTest {

    private final B68LineupScorer scorer =
            new B68LineupScorer();

    @Test
    void shouldCalculateDeterministicCompleteOutfieldScore() {

        B68LineupScorer.Input input =
                new B68LineupScorer.Input(
                        "A",
                        90.0,
                        80.0,
                        95.0,
                        70.0,
                        new BigDecimal("85"),
                        new BigDecimal("100"),
                        "AVAILABLE",
                        new BigDecimal("40"),
                        new BigDecimal("90"),
                        2
                );

        B68LineupScorer.ScoreResult first =
                scorer.score(input);

        B68LineupScorer.ScoreResult second =
                scorer.score(input);

        assertThat(first)
                .isEqualTo(second);

        assertThat(first.selectionScore())
                .isNotNull()
                .isBetween(
                        BigDecimal.ZERO,
                        new BigDecimal("100")
                );

        assertThat(first.performanceScore())
                .isEqualByComparingTo("85.00");

        assertThat(first.fixtureScore())
                .isEqualByComparingTo("60.00");

        assertThat(first.performanceSignalCount())
                .isEqualTo(4);

        assertThat(first.domainSignalCount())
                .isEqualTo(4);

        assertThat(first.evidenceTier())
                .isEqualTo("HIGH");

        assertThat(first.actionable())
                .isTrue();

        assertThat(first.exclusionReason())
                .isNull();
    }

    @Test
    void shouldOmitMissingSignalsInsteadOfInventingNeutralValues() {

        B68LineupScorer.ScoreResult result =
                scorer.score(
                        new B68LineupScorer.Input(
                                "C",
                                80.0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "UNKNOWN",
                                new BigDecimal("40"),
                                new BigDecimal("75"),
                                1
                        )
                );

        assertThat(result.performanceScore())
                .isEqualByComparingTo("80.00");

        assertThat(result.fixtureScore())
                .isEqualByComparingTo("60.00");

        /*
         * Only real performance + fixture evidence:
         * (80 * .45 + 60 * .10) / .55
         */
        assertThat(result.selectionScore())
                .isEqualByComparingTo("76.36");

        assertThat(result.domainSignalCount())
                .isEqualTo(2);

        assertThat(result.evidenceTier())
                .isEqualTo("LOW");

        assertThat(result.actionable())
                .isTrue();
    }

    @Test
    void shouldAllowMarketNewcomerToBeRankedFromRealCurrentSignalsOnly() {

        B68LineupScorer.ScoreResult result =
                scorer.score(
                        new B68LineupScorer.Input(
                                "P",
                                null,
                                null,
                                null,
                                null,
                                new BigDecimal("70"),
                                new BigDecimal("100"),
                                "AVAILABLE",
                                new BigDecimal("55"),
                                BigDecimal.ZERO,
                                0
                        )
                );

        assertThat(result.performanceScore())
                .isNull();

        assertThat(result.performanceSignalCount())
                .isZero();

        assertThat(result.selectionScore())
                .isNotNull();

        assertThat(result.evidenceTier())
                .isEqualTo("LOW");

        assertThat(result.actionable())
                .isTrue();
    }

    @Test
    void shouldExcludeHardUnavailablePlayer() {

        B68LineupScorer.ScoreResult result =
                scorer.score(
                        new B68LineupScorer.Input(
                                "D",
                                95.0,
                                95.0,
                                95.0,
                                95.0,
                                new BigDecimal("95"),
                                BigDecimal.ZERO,
                                "SUSPENDED",
                                new BigDecimal("20"),
                                new BigDecimal("95"),
                                2
                        )
                );

        assertThat(result.selectionScore())
                .isEqualByComparingTo("0.00");

        assertThat(result.actionable())
                .isFalse();

        assertThat(result.exclusionReason())
                .contains("squalificato");
    }

    @Test
    void shouldIgnoreGoalAndAssistSignalsForGoalkeepers() {

        B68LineupScorer.ScoreResult result =
                scorer.score(
                        new B68LineupScorer.Input(
                                "P",
                                80.0,
                                60.0,
                                100.0,
                                100.0,
                                null,
                                null,
                                "UNKNOWN",
                                null,
                                new BigDecimal("80"),
                                2
                        )
                );

        assertThat(result.performanceSignalCount())
                .isEqualTo(2);

        assertThat(result.performanceScore())
                .isEqualByComparingTo("70.00");

        assertThat(result.selectionScore())
                .isEqualByComparingTo("70.00");
    }

    @Test
    void shouldReturnNoEvidenceWithoutInventedScore() {

        B68LineupScorer.ScoreResult result =
                scorer.score(
                        new B68LineupScorer.Input(
                                "A",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "UNKNOWN",
                                null,
                                null,
                                0
                        )
                );

        assertThat(result.selectionScore())
                .isNull();

        assertThat(result.performanceScore())
                .isNull();

        assertThat(result.evidenceTier())
                .isEqualTo("NO_EVIDENCE");

        assertThat(result.confidence())
                .isEqualByComparingTo("0.00");

        assertThat(result.actionable())
                .isFalse();
    }

    @Test
    void shouldRejectInvalidRole() {

        assertThatThrownBy(() ->
                scorer.score(
                        new B68LineupScorer.Input(
                                "X",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0
                        )
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "Ruolo B68 non valido"
                );
    }

    @Test
    void shouldRejectOutOfRangeEvidence() {

        assertThatThrownBy(() ->
                scorer.score(
                        new B68LineupScorer.Input(
                                "A",
                                101.0,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                0
                        )
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "fantasyAverageRolePercentile"
                );
    }
}