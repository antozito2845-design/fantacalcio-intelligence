package it.fantacalciointelligence.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretSanitizerTest {

    @Test
    void redactsEveryAuthorizationSchemeWithoutTokenFragments() {
        for (String scheme : new String[] {"Bearer", "Basic", "Digest", "Custom"}) {
            String sanitized = SecretSanitizer.sanitize(
                    "Authorization: " + scheme + " token-prefix-token-suffix"
            );
            assertThat(sanitized)
                    .doesNotContain("token-prefix", "token-suffix", scheme)
                    .contains("Authorization: [REDACTED]");
        }
        assertThat(SecretSanitizer.sanitize("authorization=my-secret-token"))
                .isEqualTo("authorization=[REDACTED]");
    }

    @Test
    void removesCredentialsAndSensitiveUrlParts() {
        String sanitized = SecretSanitizer.sanitize(new IllegalStateException(
                "Authorization: Bearer abc password=hunter2 "
                        + "https://alice:secret@example.test/feed?token=abc#fragment "
                        + "jdbc:postgresql://db:5432/app?password=secret"
        ));

        assertThat(sanitized)
                .doesNotContain("abc", "hunter2", "alice", "secret", "token=", "#fragment")
                .contains("[REDACTED]");
    }

    @Test
    void limitsPersistedLength() {
        assertThat(SecretSanitizer.sanitize("x".repeat(3000))).hasSize(2000);
    }

    @Test
    void handlesNullBlankSafeAndMalformedValues() {
        assertThat(SecretSanitizer.sanitize((Throwable) null)).isEqualTo("Errore sconosciuto");
        assertThat(SecretSanitizer.sanitize("   ")).isEqualTo("Errore sconosciuto");
        assertThat(SecretSanitizer.sanitize("Messaggio operativo sicuro"))
                .isEqualTo("Messaggio operativo sicuro");
        assertThat(SecretSanitizer.sanitize("Errore su https://user:pass@[host/feed?secret=x#frag"))
                .doesNotContain("user", "pass", "secret=x", "#frag");
    }

    @Test
    void redactsAllSupportedSecretKeysAndJdbc() {
        String sanitized = SecretSanitizer.sanitize(
                "password=a passwd=b pwd=c token=d api_key=e api-key=f apikey=g secret=h "
                        + "jdbc:postgresql://db.internal/app?password=i"
        );
        assertThat(sanitized).doesNotContain("=a", "=b", "=c", "=d", "=e", "=f", "=g", "=h",
                "jdbc:postgresql", "db.internal");
    }
}
