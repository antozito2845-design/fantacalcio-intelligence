package it.fantacalciointelligence.backend.liveauction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class LiveAuctionAlternativeService {
    private final LiveAuctionDecisionPolicy policy;

    public LiveAuctionAlternativeService(LiveAuctionDecisionPolicy policy) {
        this.policy = policy;
    }

    public Optional<LiveAuctionCandidateEvaluation> dominantAlternative(
            LiveAuctionCandidateEvaluation current, int currentBid,
            List<LiveAuctionCandidateEvaluation> alternatives) {
        return alternatives.stream()
                .filter(candidate -> contextFit(current, candidate))
                .filter(candidate -> candidate.target().b51RecommendedPrice() <= candidate.target().targetPrice())
                .filter(candidate -> policy.alternativeSaving(
                        candidate.target().b51RecommendedPrice(), currentBid))
                .filter(candidate -> policy.comparableEvidence(evidence(candidate), evidence(current)))
                .sorted(Comparator
                        .comparing((LiveAuctionCandidateEvaluation c) -> c.target().fitScore()).reversed()
                        .thenComparing((LiveAuctionCandidateEvaluation c) -> calendarScore(c), Comparator.reverseOrder())
                        .thenComparing(c -> c.target().b51RecommendedPrice())
                        .thenComparing((LiveAuctionCandidateEvaluation c) -> evidence(c), Comparator.reverseOrder())
                        .thenComparing(c -> c.target().playerId()))
                .findFirst();
    }

    private boolean contextFit(LiveAuctionCandidateEvaluation current,
            LiveAuctionCandidateEvaluation alternative) {
        int fitDelta = alternative.target().fitScore().compareTo(current.target().fitScore());
        if ("P".equals(current.target().role()) && fitDelta < 0) {
            boolean closeFit = alternative.target().fitScore().doubleValue()
                    >= current.target().fitScore().doubleValue()
                            - LiveAuctionDecisionPolicy.GOALKEEPER_MAX_FIT_COMPENSATION;
            boolean materiallyBetterCalendar = calendarScore(alternative) >= calendarScore(current)
                    + LiveAuctionDecisionPolicy.GOALKEEPER_CALENDAR_IMPROVEMENT;
            if (!(closeFit && materiallyBetterCalendar)) return false;
        } else if (fitDelta < 0) {
            return false;
        }
        if ("A".equals(current.target().role()) && current.calendar() != null
                && alternative.calendar() != null
                && alternative.calendar().hardOverlapCount() > current.calendar().hardOverlapCount()) {
            return false;
        }
        return true;
    }

    private double calendarScore(LiveAuctionCandidateEvaluation candidate) {
        return candidate.calendar() == null || candidate.calendar().calendarCombinationScore() == null
                ? -1 : candidate.calendar().calendarCombinationScore().doubleValue();
    }

    private int evidence(LiveAuctionCandidateEvaluation candidate) {
        return candidate.strategic() == null ? 0 : candidate.strategic().evidenceCompleteness().intValue();
    }
}
