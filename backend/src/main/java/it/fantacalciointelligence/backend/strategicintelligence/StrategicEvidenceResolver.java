package it.fantacalciointelligence.backend.strategicintelligence;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class StrategicEvidenceResolver {
    public <T> Resolution<T> resolve(List<T> observations, Function<T, ?> metricKey) {
        List<T> supplied = observations.stream().filter(item -> metricKey.apply(item) != null).toList();
        if (supplied.isEmpty()) return new Resolution<>(null, false);
        Object first = metricKey.apply(supplied.getFirst());
        boolean conflict = supplied.stream().map(metricKey).anyMatch(value -> !Objects.equals(first, value));
        return conflict ? new Resolution<>(null, true) : new Resolution<>(supplied.getFirst(), false);
    }
    public record Resolution<T>(T observation, boolean conflict) {}
}
