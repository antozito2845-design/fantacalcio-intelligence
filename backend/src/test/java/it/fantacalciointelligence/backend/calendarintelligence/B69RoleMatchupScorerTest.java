package it.fantacalciointelligence.backend.calendarintelligence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class B69RoleMatchupScorerTest {

    private final B69RoleMatchupScorer scorer =
            new B69RoleMatchupScorer();

    @Test
    void completeForwardScoreIsDeterministicAndExact() {

        B69RoleMatchupScorer.MatchupResult result =
                scorer.score(
                        input(
                                "A",
                                true,
                                80,
                                60,
                                60,
                                55,
                                70,
                                80,
                                50
                        )
                );

        assertThat(
                result.difficultyScore()
        ).isEqualByComparingTo(
                "55.00"
        );

        assertThat(
                result.difficulty()
        ).isEqualTo(
                CalendarDifficulty.MEDIUM
        );

        assertThat(
                result.evidenceCoverage()
        ).isEqualByComparingTo(
                "100.00"
        );

        assertThat(
                result.evidenceTier()
        ).isEqualTo(
                "HIGH"
        );

        assertThat(
                result.signalCount()
        ).isEqualTo(4);

        assertThat(
                result.maximumSignalCount()
        ).isEqualTo(4);
    }

    @Test
    void missingSignalsAreOmittedAndWeightsAreRenormalized() {

        B69RoleMatchupScorer.MatchupResult result =
                scorer.score(
                        new B69RoleMatchupScorer.Input(
                                "A",
                                true,
                                null,
                                null,
                                bd(60),
                                null,
                                bd(80),
                                null,
                                null
                        )
                );

        assertThat(
                result.difficultyScore()
        ).isEqualByComparingTo(
                "73.85"
        );

        assertThat(
                result.evidenceCoverage()
        ).isEqualByComparingTo(
                "65.00"
        );

        assertThat(
                result.evidenceTier()
        ).isEqualTo(
                "LOW"
        );

        assertThat(
                result.signalCount()
        ).isEqualTo(2);
    }

    @Test
    void noEvidenceNeverCreatesNeutralDifficulty() {

        B69RoleMatchupScorer.MatchupResult result =
                scorer.score(
                        new B69RoleMatchupScorer.Input(
                                "P",
                                true,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                );

        assertThat(
                result.difficultyScore()
        ).isNull();

        assertThat(
                result.difficulty()
        ).isEqualTo(
                CalendarDifficulty.UNKNOWN
        );

        assertThat(
                result.evidenceTier()
        ).isEqualTo(
                "NO_EVIDENCE"
        );

        assertThat(
                result.evidenceCoverage()
        ).isEqualByComparingTo(
                "0.00"
        );
    }

    @Test
    void strongerOpponentAttackMakesGoalkeeperMatchupHarder() {

        var weakAttack =
                scorer.score(
                        input(
                                "P",
                                true,
                                60,
                                70,
                                60,
                                40,
                                60,
                                70,
                                55
                        )
                );

        var strongAttack =
                scorer.score(
                        input(
                                "P",
                                true,
                                60,
                                70,
                                60,
                                90,
                                60,
                                70,
                                55
                        )
                );

        assertThat(
                strongAttack.difficultyScore()
        ).isGreaterThan(
                weakAttack.difficultyScore()
        );
    }

    @Test
    void strongerOpponentAttackMakesDefenderMatchupHarder() {

        var weakAttack =
                scorer.score(
                        input(
                                "D",
                                false,
                                60,
                                70,
                                60,
                                40,
                                60,
                                75,
                                50
                        )
                );

        var strongAttack =
                scorer.score(
                        input(
                                "D",
                                false,
                                60,
                                70,
                                60,
                                90,
                                60,
                                75,
                                50
                        )
                );

        assertThat(
                strongAttack.difficultyScore()
        ).isGreaterThan(
                weakAttack.difficultyScore()
        );
    }

    @Test
    void strongerOpponentDefenseMakesForwardMatchupHarder() {

        var weakDefense =
                scorer.score(
                        input(
                                "A",
                                true,
                                75,
                                60,
                                60,
                                60,
                                35,
                                70,
                                50
                        )
                );

        var strongDefense =
                scorer.score(
                        input(
                                "A",
                                true,
                                75,
                                60,
                                60,
                                60,
                                90,
                                70,
                                50
                        )
                );

        assertThat(
                strongDefense.difficultyScore()
        ).isGreaterThan(
                weakDefense.difficultyScore()
        );
    }

    @Test
    void strongerOpponentDefenseMakesMidfielderMatchupHarder() {

        var weakDefense =
                scorer.score(
                        input(
                                "C",
                                true,
                                75,
                                60,
                                60,
                                60,
                                35,
                                70,
                                50
                        )
                );

        var strongDefense =
                scorer.score(
                        input(
                                "C",
                                true,
                                75,
                                60,
                                60,
                                60,
                                90,
                                70,
                                50
                        )
                );

        assertThat(
                strongDefense.difficultyScore()
        ).isGreaterThan(
                weakDefense.difficultyScore()
        );
    }

    @Test
    void strongerOwnDefenseReducesGoalkeeperAndDefenderDifficulty() {

        var weakGoalkeeperSupport =
                scorer.score(
                        input(
                                "P",
                                true,
                                60,
                                30,
                                70,
                                70,
                                60,
                                75,
                                55
                        )
                );

        var strongGoalkeeperSupport =
                scorer.score(
                        input(
                                "P",
                                true,
                                60,
                                90,
                                70,
                                70,
                                60,
                                75,
                                55
                        )
                );

        var weakDefenderSupport =
                scorer.score(
                        input(
                                "D",
                                true,
                                60,
                                30,
                                70,
                                70,
                                60,
                                75,
                                55
                        )
                );

        var strongDefenderSupport =
                scorer.score(
                        input(
                                "D",
                                true,
                                60,
                                90,
                                70,
                                70,
                                60,
                                75,
                                55
                        )
                );

        assertThat(
                strongGoalkeeperSupport
                        .difficultyScore()
        ).isLessThan(
                weakGoalkeeperSupport
                        .difficultyScore()
        );

        assertThat(
                strongDefenderSupport
                        .difficultyScore()
        ).isLessThan(
                weakDefenderSupport
                        .difficultyScore()
        );
    }

    @Test
    void strongerOwnAttackReducesMidfielderAndForwardDifficulty() {

        var weakMidfieldSupport =
                scorer.score(
                        input(
                                "C",
                                true,
                                30,
                                60,
                                70,
                                65,
                                70,
                                75,
                                55
                        )
                );

        var strongMidfieldSupport =
                scorer.score(
                        input(
                                "C",
                                true,
                                90,
                                60,
                                70,
                                65,
                                70,
                                75,
                                55
                        )
                );

        var weakForwardSupport =
                scorer.score(
                        input(
                                "A",
                                true,
                                30,
                                60,
                                70,
                                65,
                                70,
                                75,
                                55
                        )
                );

        var strongForwardSupport =
                scorer.score(
                        input(
                                "A",
                                true,
                                90,
                                60,
                                70,
                                65,
                                70,
                                75,
                                55
                        )
                );

        assertThat(
                strongMidfieldSupport
                        .difficultyScore()
        ).isLessThan(
                weakMidfieldSupport
                        .difficultyScore()
        );

        assertThat(
                strongForwardSupport
                        .difficultyScore()
        ).isLessThan(
                weakForwardSupport
                        .difficultyScore()
        );
    }

    @Test
    void homeClubUsesOpponentAwayRating() {

        var result =
                scorer.score(
                        input(
                                "P",
                                true,
                                60,
                                60,
                                60,
                                60,
                                60,
                                90,
                                35
                        )
                );

        assertThat(
                result.opponentVenueRating()
        ).isEqualByComparingTo(
                "35"
        );
    }

    @Test
    void awayClubUsesOpponentHomeRating() {

        var result =
                scorer.score(
                        input(
                                "P",
                                false,
                                60,
                                60,
                                60,
                                60,
                                60,
                                90,
                                35
                        )
                );

        assertThat(
                result.opponentVenueRating()
        ).isEqualByComparingTo(
                "90"
        );
    }

    @Test
    void sameFixtureProducesRoleSpecificDifficulty() {

        var goalkeeper =
                scorer.score(
                        input(
                                "P",
                                true,
                                75,
                                70,
                                60,
                                85,
                                40,
                                70,
                                50
                        )
                );

        var forward =
                scorer.score(
                        input(
                                "A",
                                true,
                                75,
                                70,
                                60,
                                85,
                                40,
                                70,
                                50
                        )
                );

        assertThat(
                goalkeeper.difficultyScore()
        ).isNotEqualByComparingTo(
                forward.difficultyScore()
        );
    }

    @Test
    void repeatedCalculationIsDeterministic() {

        var input =
                input(
                        "C",
                        false,
                        70,
                        65,
                        72,
                        68,
                        74,
                        80,
                        60
                );

        assertThat(
                scorer.score(input)
        ).isEqualTo(
                scorer.score(input)
        );
    }

    @Test
    void invalidFantasyRoleIsRejected() {

        assertThatThrownBy(() ->
                scorer.score(
                        input(
                                "X",
                                true,
                                60,
                                60,
                                60,
                                60,
                                60,
                                60,
                                60
                        )
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "Invalid fantasy role"
                );
    }

    @Test
    void outOfRangeRatingsAreRejected() {

        assertThatThrownBy(() ->
                scorer.score(
                        input(
                                "A",
                                true,
                                101,
                                60,
                                60,
                                60,
                                60,
                                60,
                                60
                        )
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "between 0 and 100"
                );
    }

    private B69RoleMatchupScorer.Input input(
            String role,
            boolean home,
            int ownAttack,
            int ownDefense,
            int opponentOverall,
            int opponentAttack,
            int opponentDefense,
            int opponentHome,
            int opponentAway
    ) {

        return new B69RoleMatchupScorer.Input(
                role,
                home,
                bd(ownAttack),
                bd(ownDefense),
                bd(opponentOverall),
                bd(opponentAttack),
                bd(opponentDefense),
                bd(opponentHome),
                bd(opponentAway)
        );
    }

    private BigDecimal bd(
            int value
    ) {
        return BigDecimal.valueOf(
                value
        );
    }
}