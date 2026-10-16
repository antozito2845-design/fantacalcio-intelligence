package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceIngestionSanitizationTest {

    @Test
    void redactsBearerAuthorizationHeader() {
        assertThat(safe("Authorization: Bearer SUPER_SECRET"))
                .doesNotContain("Bearer", "SUPER_SECRET")
                .contains("[REDACTED]");
    }

    @Test
    void redactsBasicAuthorizationValue() {
        assertThat(safe("authorization=Basic ABCDEF"))
                .doesNotContain("Basic", "ABCDEF")
                .contains("[REDACTED]");
    }

    @Test
    void redactsPostgresqlJdbcUrl() {
        assertThat(safe("jdbc:postgresql://db-user:db-pass@secret-host:5432/prod"))
                .doesNotContain("db-pass", "secret-host", "jdbc:postgresql")
                .isEqualTo("[REDACTED]");
    }

    @Test
    void stripsHttpUrlUserInfoQueryAndFragment() {
        String sanitized = safe(
                "https://user:pass@example.test/feed.csv?api_key=SUPER_SECRET#frag"
        );
        assertThat(sanitized)
                .isEqualTo("https://example.test/feed.csv")
                .doesNotContain("user", "pass", "SUPER_SECRET", "frag");
    }

    @Test
    void redactsSensitiveKeyValue() {
        assertThat(safe("password=SUPER_SECRET"))
                .isEqualTo("password=[REDACTED]");
    }

    @Test
    void preservesOrdinarySafeMessage() {
        assertThat(safe("Import CSV fallito per struttura non valida"))
                .isEqualTo("Import CSV fallito per struttura non valida");
    }

    private String safe(String message) {
        return SourceIngestionService.safe(new IllegalStateException(message));
    }
}
