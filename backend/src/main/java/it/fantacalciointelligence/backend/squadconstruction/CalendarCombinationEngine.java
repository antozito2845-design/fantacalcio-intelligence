package it.fantacalciointelligence.backend.squadconstruction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Complements the existing fixture/pairing services. Inputs are their
 * difficulty scores; this class does not query or duplicate fixture data.
 */
@Component
public class CalendarCombinationEngine {
    public Result evaluate(List<List<BigDecimal>> playerDifficulties) {
        if (playerDifficulties == null || playerDifficulties.size() < 2
                || playerDifficulties.stream().anyMatch(List::isEmpty)) {
            return new Result(null, EvidenceStatus.NOT_AVAILABLE, 0);
        }
        int matchdays = playerDifficulties.stream().mapToInt(List::size).min().orElse(0);
        if (matchdays == 0) return new Result(null, EvidenceStatus.NOT_AVAILABLE, 0);
        double total = 0;
        for (int day = 0; day < matchdays; day++) {
            final int index = day;
            double best = playerDifficulties.stream().mapToDouble(p -> p.get(index).doubleValue()).min().orElse(100);
            long playable = playerDifficulties.stream().filter(p -> p.get(index).doubleValue() <= 60).count();
            total += (100 - best) * .75 + Math.min(2, playable) * 12.5;
        }
        double roleCapped = Math.max(0, Math.min(100, total / matchdays));
        return new Result(BigDecimal.valueOf(roleCapped).setScale(2, RoundingMode.HALF_UP),
                EvidenceStatus.AVAILABLE, matchdays);
    }

    public record Result(BigDecimal score, EvidenceStatus evidence, int matchdays) { }
}
