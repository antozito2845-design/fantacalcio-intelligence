package it.fantacalciointelligence.backend.strategic;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class RealPositionNormalizerTest {
    private final RealPositionNormalizer normalizer = new RealPositionNormalizer();

    @Test void mapsOnlyExplicitUnambiguousValues() {
        assertThat(normalizer.normalize("right wing-back")).isEqualTo(RealPosition.RWB);
        assertThat(normalizer.normalize("CB")).isEqualTo(RealPosition.CB);
    }

    @Test void missingAndAmbiguousValuesStayUnknown() {
        assertThat(normalizer.normalize(null)).isEqualTo(RealPosition.UNKNOWN);
        assertThat(normalizer.normalize("defender")).isEqualTo(RealPosition.UNKNOWN);
        assertThat(normalizer.normalize("D/C")).isEqualTo(RealPosition.UNKNOWN);
    }
}
