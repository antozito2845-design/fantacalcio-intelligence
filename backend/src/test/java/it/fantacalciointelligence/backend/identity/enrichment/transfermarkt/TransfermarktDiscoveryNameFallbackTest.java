package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.simple.JdbcClient;

class TransfermarktDiscoveryNameFallbackTest {

    private TransfermarktDiscoveryService service() {
        return new TransfermarktDiscoveryService(
                Mockito.mock(JdbcClient.class),
                new TransfermarktDiscoveryProperties(),
                Mockito.mock(TransfermarktDiscoveryClient.class),
                new TransfermarktSearchParser(),
                new TransfermarktProfileParser(),
                new TransfermarktCandidateMatcher()
        );
    }

    @Test
    void singleInitialCreatesSurnameFallbackQuery() {
        assertThat(
                service().searchQueries(
                        "Sulemana I."
                )
        ).containsExactly(
                "Sulemana I.",
                "Sulemana"
        );
    }

    @Test
    void compoundInitialSuffixCreatesBaseFallbackQuery() {
        assertThat(
                service().searchQueries(
                        "Ederson D.S."
                )
        ).containsExactly(
                "Ederson D.S.",
                "Ederson"
        );
    }

    @Test
    void ordinarySurnameUsesOnlyOriginalQuery() {
        assertThat(
                service().searchQueries(
                        "Bellanova"
                )
        ).containsExactly(
                "Bellanova"
        );
    }

    @Test
    void firstNameInitialDisambiguatesSameSurname() {
        TransfermarktDiscoveryService service =
                service();

        assertThat(
                service.initialCompatible(
                        "Sulemana I.",
                        "Ibrahim Sulemana"
                )
        ).isTrue();

        assertThat(
                service.initialCompatible(
                        "Sulemana I.",
                        "Kamaldeen Sulemana"
                )
        ).isFalse();
    }

    @Test
    void compoundSuffixIsNotTreatedAsFirstNameInitial() {
        assertThat(
                service().initialCompatible(
                        "Ederson D.S.",
                        "Ederson JosÃ© dos Santos LourenÃ§o da Silva"
                )
        ).isTrue();
    }
}