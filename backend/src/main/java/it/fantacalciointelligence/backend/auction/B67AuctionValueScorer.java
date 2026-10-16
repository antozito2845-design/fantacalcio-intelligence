package it.fantacalciointelligence.backend.auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class B67AuctionValueScorer {

    public ScoreResult score(
            String role,
            Double fvmRolePercentile,
            Double quotationRolePercentile,
            Double fantasyAverageRolePercentile,
            Double usageRolePercentile,
            Double goalRateRolePercentile,
            Double assistRateRolePercentile
    ) {

        List<Double> marketSignals = new ArrayList<>();

        addIfPresent(
                marketSignals,
                fvmRolePercentile
        );

        addIfPresent(
                marketSignals,
                quotationRolePercentile
        );

        List<Double> performanceSignals = new ArrayList<>();

        addIfPresent(
                performanceSignals,
                fantasyAverageRolePercentile
        );

        addIfPresent(
                performanceSignals,
                usageRolePercentile
        );

        if (!"P".equals(role)) {

            addIfPresent(
                    performanceSignals,
                    goalRateRolePercentile
            );

            addIfPresent(
                    performanceSignals,
                    assistRateRolePercentile
            );
        }

        Double marketScore =
                medianOrNull(
                        marketSignals
                );

        Double performanceScore =
                medianOrNull(
                        performanceSignals
                );

        Double auctionValueScore;

        if (
                marketScore != null
                && performanceScore != null
        ) {

            /*
             * Deliberately equal DOMAIN weighting.
             *
             * The number of individual historical features must not
             * silently give the performance domain more influence
             * than Fantacalcio market evidence.
             */
            auctionValueScore =
                    (
                            marketScore
                            + performanceScore
                    ) / 2.0;

        } else if (marketScore != null) {

            auctionValueScore =
                    marketScore;

        } else {

            auctionValueScore =
                    performanceScore;
        }

        return new ScoreResult(
                auctionValueScore,
                performanceSignals.size(),
                marketSignals.size()
                        + performanceSignals.size()
        );
    }

    public Double marketSignal(
            Double fvmRolePercentile,
            Double quotationRolePercentile
    ) {

        List<Double> values =
                new ArrayList<>();

        addIfPresent(
                values,
                fvmRolePercentile
        );

        addIfPresent(
                values,
                quotationRolePercentile
        );

        return medianOrNull(values);
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

        values.sort(
                Comparator.naturalOrder()
        );

        int size =
                values.size();

        if (size % 2 == 1) {
            return values.get(
                    size / 2
            );
        }

        return (
                values.get(
                        size / 2 - 1
                )
                + values.get(
                        size / 2
                )
        ) / 2.0;
    }

    public record ScoreResult(
            Double auctionValueScore,
            int performanceSignalCount,
            int totalSignalCount
    ) {
    }
}