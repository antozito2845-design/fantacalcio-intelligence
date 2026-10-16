package it.fantacalciointelligence.backend.auction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class B67AuctionValueScorerTest {

    private final B67AuctionValueScorer scorer =
            new B67AuctionValueScorer();

    @Test
    void shouldCombineMarketAndPerformanceAsEqualDomains() {

        B67AuctionValueScorer.ScoreResult result =
                scorer.score(
                        "A",

                        100.0,
                        100.0,

                        0.0,
                        0.0,
                        0.0,
                        100.0
                );

        /*
         * Market median = 100.
         * Performance median = 0.
         *
         * Equal domain combination = 50.
         *
         * A flat median over all six signals would incorrectly
         * produce 0 and would overweight historical feature count.
         */
        assertThat(
                result.auctionValueScore()
        ).isEqualTo(50.0);

        assertThat(
                result.performanceSignalCount()
        ).isEqualTo(4);

        assertThat(
                result.totalSignalCount()
        ).isEqualTo(6);
    }

    @Test
    void shouldUseMedianInsidePerformanceDomain() {

        B67AuctionValueScorer.ScoreResult result =
                scorer.score(
                        "C",

                        null,
                        null,

                        90.0,
                        70.0,
                        50.0,
                        30.0
                );

        assertThat(
                result.auctionValueScore()
        ).isEqualTo(60.0);

        assertThat(
                result.performanceSignalCount()
        ).isEqualTo(4);
    }

    @Test
    void shouldKeepUnknownNullInsteadOfTurningItIntoZero() {

        B67AuctionValueScorer.ScoreResult result =
                scorer.score(
                        "D",

                        40.0,
                        20.0,

                        null,
                        null,
                        null,
                        null
                );

        assertThat(
                result.auctionValueScore()
        ).isEqualTo(30.0);

        assertThat(
                result.performanceSignalCount()
        ).isZero();

        assertThat(
                result.totalSignalCount()
        ).isEqualTo(2);
    }

    @Test
    void shouldAllowMarketOnlyGoalkeeperLikePenev() {

        B67AuctionValueScorer.ScoreResult result =
                scorer.score(
                        "P",

                        10.0,
                        10.0,

                        null,
                        null,
                        null,
                        null
                );

        assertThat(
                result.auctionValueScore()
        ).isEqualTo(10.0);

        assertThat(
                result.performanceSignalCount()
        ).isZero();
    }

    @Test
    void goalkeeperShouldIgnoreGoalAndAssistSignals() {

        B67AuctionValueScorer.ScoreResult result =
                scorer.score(
                        "P",

                        60.0,
                        40.0,

                        null,
                        null,

                        100.0,
                        100.0
                );

        assertThat(
                result.auctionValueScore()
        ).isEqualTo(50.0);

        assertThat(
                result.performanceSignalCount()
        ).isZero();
    }

    @Test
    void zeroMustRemainRealEvidence() {

        B67AuctionValueScorer.ScoreResult result =
                scorer.score(
                        "C",

                        0.0,
                        0.0,

                        null,
                        null,
                        null,
                        null
                );

        assertThat(
                result.auctionValueScore()
        ).isZero();
    }

    @Test
    void shouldReturnNullOnlyWhenEveryEvidenceSignalIsMissing() {

        B67AuctionValueScorer.ScoreResult result =
                scorer.score(
                        "A",

                        null,
                        null,

                        null,
                        null,
                        null,
                        null
                );

        assertThat(
                result.auctionValueScore()
        ).isNull();

        assertThat(
                result.totalSignalCount()
        ).isZero();
    }
}