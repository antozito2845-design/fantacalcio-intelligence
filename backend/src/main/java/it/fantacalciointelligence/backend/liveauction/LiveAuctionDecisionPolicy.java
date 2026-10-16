package it.fantacalciointelligence.backend.liveauction;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

@Component
public class LiveAuctionDecisionPolicy {
    public static final BigDecimal IMMEDIATE_BUY_TARGET_RATIO = new BigDecimal("0.75");
    public static final int STRONG_FIT_THRESHOLD = 65;
    public static final int CAUTION_FIT_THRESHOLD = 70;
    public static final int STRATEGIC_SUPPORT_THRESHOLD = 65;
    public static final int CONTEXTUAL_MAX_STOP_DISTANCE = 1;
    public static final int ALTERNATIVE_MINIMUM_SAVING = 5;
    public static final int ALTERNATIVE_EVIDENCE_TOLERANCE = 25;
    public static final int GOALKEEPER_MAX_FIT_COMPENSATION = 5;
    public static final int GOALKEEPER_CALENDAR_IMPROVEMENT = 10;
    public static final int AVAILABLE_CONFIDENCE_THRESHOLD = 75;
    public static final int PARTIAL_CONFIDENCE_THRESHOLD = 40;
    public static final BigDecimal B54_CONFIDENCE_WEIGHT = new BigDecimal("0.55");
    public static final BigDecimal B56_CONFIDENCE_WEIGHT = new BigDecimal("0.20");
    public static final BigDecimal B57_CONFIDENCE_WEIGHT = new BigDecimal("0.15");
    public static final BigDecimal ALTERNATIVE_CONFIDENCE_WEIGHT = new BigDecimal("0.10");
    public static final int MISSING_MATCHDAY_CONFIDENCE_CAP = 75;

    public boolean immediateBuy(int proposedBid, int targetPrice, BigDecimal fit) {
        return BigDecimal.valueOf(proposedBid).compareTo(
                BigDecimal.valueOf(targetPrice).multiply(IMMEDIATE_BUY_TARGET_RATIO)) <= 0
                && fit.compareTo(BigDecimal.valueOf(STRONG_FIT_THRESHOLD)) >= 0;
    }

    public boolean cautionFit(BigDecimal fit) {
        return fit.compareTo(BigDecimal.valueOf(CAUTION_FIT_THRESHOLD)) >= 0;
    }

    public boolean strategicSupport(BigDecimal score, String status) {
        return score != null && score.compareTo(BigDecimal.valueOf(STRATEGIC_SUPPORT_THRESHOLD)) >= 0
                && "AVAILABLE".equals(status);
    }

    public boolean alternativeSaving(int alternativePrice, int proposedBid) {
        return alternativePrice + ALTERNATIVE_MINIMUM_SAVING <= proposedBid;
    }

    public boolean comparableEvidence(int alternativeEvidence, int currentEvidence) {
        return alternativeEvidence + ALTERNATIVE_EVIDENCE_TOLERANCE >= currentEvidence;
    }

    public BigDecimal confidence(BigDecimal b54, BigDecimal b56, BigDecimal b57,
            boolean alternativesAvailable, boolean matchdaySupplied) {
        BigDecimal value = b54.multiply(B54_CONFIDENCE_WEIGHT)
                .add(b56.multiply(B56_CONFIDENCE_WEIGHT))
                .add(b57.multiply(B57_CONFIDENCE_WEIGHT))
                .add(BigDecimal.valueOf(alternativesAvailable ? 100 : 50)
                        .multiply(ALTERNATIVE_CONFIDENCE_WEIGHT));
        return !matchdaySupplied && value.compareTo(BigDecimal.valueOf(MISSING_MATCHDAY_CONFIDENCE_CAP)) > 0
                ? BigDecimal.valueOf(MISSING_MATCHDAY_CONFIDENCE_CAP) : value;
    }
}
