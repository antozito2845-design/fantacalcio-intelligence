package it.fantacalciointelligence.backend.liveauction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class LiveAuctionDecisionPolicyTest {
    private final LiveAuctionDecisionPolicy policy = new LiveAuctionDecisionPolicy();

    @Test
    void immediateBuyRatioAndStrongFitHaveExactBoundaries() {
        assertThat(policy.immediateBuy(75, 100, bd(65))).isTrue();
        assertThat(policy.immediateBuy(76, 100, bd(65))).isFalse();
        assertThat(policy.immediateBuy(75, 100, new BigDecimal("64.99"))).isFalse();
    }

    @Test
    void cautionFitHasExactBoundary() {
        assertThat(policy.cautionFit(bd(70))).isTrue();
        assertThat(policy.cautionFit(new BigDecimal("69.99"))).isFalse();
    }

    @Test
    void strategicSupportRequiresAvailableAndExactThreshold() {
        assertThat(policy.strategicSupport(bd(65), "AVAILABLE")).isTrue();
        assertThat(policy.strategicSupport(new BigDecimal("64.99"), "AVAILABLE")).isFalse();
        assertThat(policy.strategicSupport(bd(100), "PARTIAL")).isFalse();
    }

    private BigDecimal bd(int value) { return BigDecimal.valueOf(value); }
}
