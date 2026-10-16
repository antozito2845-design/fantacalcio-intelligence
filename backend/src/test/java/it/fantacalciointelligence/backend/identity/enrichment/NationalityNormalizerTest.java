package it.fantacalciointelligence.backend.identity.enrichment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NationalityNormalizerTest {

    @Test
    void shouldResolveItalianNationality() {
        assertThat(
                NationalityNormalizer.resolveCode(
                        "Italia",
                        null,
                        null
                )
        ).isEqualTo("IT");
    }

    @Test
    void shouldResolveBritishVariants() {
        assertThat(
                NationalityNormalizer.resolveCode(
                        "Inghilterra",
                        null,
                        null
                )
        ).isEqualTo("GB");

        assertThat(
                NationalityNormalizer.resolveCode(
                        "Scozia",
                        null,
                        null
                )
        ).isEqualTo("GB");

        assertThat(
                NationalityNormalizer.resolveCode(
                        "England",
                        null,
                        null
                )
        ).isEqualTo("GB");
    }

    @Test
    void shouldResolveKosovo() {
        assertThat(
                NationalityNormalizer.resolveCode(
                        "Kosovo",
                        null,
                        null
                )
        ).isEqualTo("XK");
    }

    @Test
    void shouldUsePrimaryNationalityForMultipleValues() {
        assertThat(
                NationalityNormalizer.resolveCode(
                        "Francia, Guinea-Bissau",
                        null,
                        null
                )
        ).isEqualTo("FR");
    }

    @Test
    void shouldApplyVerifiedVazOverride() {
        assertThat(
                NationalityNormalizer.normalizeNationality(
                        "Frabcia, Guinea Bissau",
                        "1131161"
                )
        ).isEqualTo(
                "Francia, Guinea-Bissau"
        );

        assertThat(
                NationalityNormalizer.resolveCode(
                        "Frabcia, Guinea Bissau",
                        null,
                        "1131161"
                )
        ).isEqualTo("FR");
    }

    @Test
    void shouldApplyVerifiedPessinaOverride() {
        assertThat(
                NationalityNormalizer.normalizeNationality(
                        "Bologna",
                        "992569"
                )
        ).isEqualTo("Italia");

        assertThat(
                NationalityNormalizer.resolveCode(
                        "Bologna",
                        null,
                        "992569"
                )
        ).isEqualTo("IT");
    }

    @Test
    void shouldPreferCertifiedMappingOverWrongExplicitCode() {
        assertThat(
                NationalityNormalizer.resolveCode(
                        "Italia",
                        "FR",
                        null
                )
        ).isEqualTo("IT");
    }

    @Test
    void shouldFallbackToExplicitCodeForUnknownNationality() {
        assertThat(
                NationalityNormalizer.resolveCode(
                        "Unknown Country",
                        "zz",
                        null
                )
        ).isEqualTo("ZZ");
    }
}