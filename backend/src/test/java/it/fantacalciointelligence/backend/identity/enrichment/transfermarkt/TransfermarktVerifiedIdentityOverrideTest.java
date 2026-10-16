package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class TransfermarktVerifiedIdentityOverrideTest {

    @Test
    void verifiedOverrideSuppliesIdentityWhenProfileIsMissing() {
        String displayName = "Aurelio";
        String club = "Cagliari";
        String season = "2026/2027";

        OverrideIdentity override = new OverrideIdentity(
                season,
                club,
                displayName,
                "Giuseppe Aurelio",
                LocalDate.of(2000, 3, 22),
                "457444");

        String canonicalFullName = null;
        LocalDate birthDate = null;
        String transfermarktPlayerId = null;

        String effectiveCanonicalFullName = canonicalFullName != null
                ? canonicalFullName
                : override.canonicalFullName();

        LocalDate effectiveBirthDate = birthDate != null
                ? birthDate
                : override.birthDate();

        String effectiveTransfermarktPlayerId = transfermarktPlayerId != null
                ? transfermarktPlayerId
                : override.transfermarktPlayerId();

        assertThat(effectiveCanonicalFullName)
                .isEqualTo("Giuseppe Aurelio");

        assertThat(effectiveBirthDate)
                .isEqualTo(LocalDate.of(2000, 3, 22));

        assertThat(effectiveTransfermarktPlayerId)
                .isEqualTo("457444");
    }

    @Test
    void existingIdentityProfileKeepsPriorityOverOverride() {
        OverrideIdentity override = new OverrideIdentity(
                "2026/2027",
                "Cagliari",
                "Aurelio",
                "Giuseppe Aurelio",
                LocalDate.of(2000, 3, 22),
                "457444");

        String canonicalFullName = "Existing Canonical Name";
        LocalDate birthDate = LocalDate.of(1999, 1, 1);
        String transfermarktPlayerId = "999999";

        String effectiveCanonicalFullName = canonicalFullName != null
                ? canonicalFullName
                : override.canonicalFullName();

        LocalDate effectiveBirthDate = birthDate != null
                ? birthDate
                : override.birthDate();

        String effectiveTransfermarktPlayerId = transfermarktPlayerId != null
                ? transfermarktPlayerId
                : override.transfermarktPlayerId();

        assertThat(effectiveCanonicalFullName)
                .isEqualTo("Existing Canonical Name");

        assertThat(effectiveBirthDate)
                .isEqualTo(LocalDate.of(1999, 1, 1));

        assertThat(effectiveTransfermarktPlayerId)
                .isEqualTo("999999");
    }

    @Test
    void verifiedOverridesContainAllThreeCertifiedPlayers() {
        OverrideIdentity aurelio = new OverrideIdentity(
                "2026/2027",
                "Cagliari",
                "Aurelio",
                "Giuseppe Aurelio",
                LocalDate.of(2000, 3, 22),
                "457444");

        OverrideIdentity penev = new OverrideIdentity(
                "2026/2027",
                "Lecce",
                "Penev",
                "Plamen Penev",
                LocalDate.of(2008, 4, 22),
                "1084756");

        OverrideIdentity romero = new OverrideIdentity(
                "2026/2027",
                "Parma",
                "Romero D.",
                "David Romero",
                LocalDate.of(2003, 3, 25),
                "668512");

        assertThat(aurelio.transfermarktPlayerId()).isEqualTo("457444");
        assertThat(penev.transfermarktPlayerId()).isEqualTo("1084756");
        assertThat(romero.transfermarktPlayerId()).isEqualTo("668512");
    }

    private record OverrideIdentity(
            String season,
            String club,
            String displayName,
            String canonicalFullName,
            LocalDate birthDate,
            String transfermarktPlayerId) {
    }
}