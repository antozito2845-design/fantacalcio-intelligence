package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.simple.JdbcClient;

class TransfermarktDiscoveryIdentityVerificationTest {

    private final TransfermarktDiscoveryService service =
            new TransfermarktDiscoveryService(
                    Mockito.mock(JdbcClient.class),
                    new TransfermarktDiscoveryProperties(),
                    Mockito.mock(TransfermarktDiscoveryClient.class),
                    new TransfermarktSearchParser(),
                    new TransfermarktProfileParser(),
                    new TransfermarktCandidateMatcher()
            );

    @Test
    void strongIdentitySurvivesTemporarilyDifferentRoster() {
        var verification = service.verifyProfile(
                player(
                        "Pellegrini Lu.",
                        "Lazio",
                        "Luca Pellegrini",
                        LocalDate.of(1999, 3, 7),
                        "346567"
                ),
                candidate("346567", "Luca Pellegrini"),
                profile(
                        "346567",
                        "Luca Pellegrini",
                        LocalDate.of(1999, 3, 7),
                        "Juventus FC"
                )
        );

        assertThat(verification.status())
                .isEqualTo("MATCHED_VERIFIED");

        assertThat(verification.reason())
                .contains("roster Transfermarkt temporalmente diverso");
    }

    @Test
    void strongIdentitySurvivesUnreadableRoster() {
        var verification = service.verifyProfile(
                player(
                        "Pessina Mas.",
                        "Bologna",
                        "Massimo Pessina",
                        LocalDate.of(2007, 12, 25),
                        "992569"
                ),
                candidate("992569", "Massimo Pessina"),
                profile(
                        "992569",
                        "Massimo Pessina",
                        LocalDate.of(2007, 12, 25),
                        null
                )
        );

        assertThat(verification.status())
                .isEqualTo("MATCHED_VERIFIED");
    }

    @Test
    void birthDateMismatchRejectsKnownTransfermarktId() {
        var verification = service.verifyProfile(
                player(
                        "David",
                        "Juventus",
                        "Jonathan David",
                        LocalDate.of(2000, 1, 14),
                        "533738"
                ),
                candidate("533738", "Jonathan David"),
                profile(
                        "533738",
                        "Jonathan David",
                        LocalDate.of(1999, 1, 14),
                        "Juventus FC"
                )
        );

        assertThat(verification.status())
                .isEqualTo("NOT_FOUND");

        assertThat(verification.reason())
                .contains("Data di nascita");
    }

    @Test
    void canonicalNameStillRejectsDifferentPerson() {
        var verification = service.verifyProfile(
                player(
                        "Mendy P.",
                        "Cagliari",
                        "Paul Mendy",
                        LocalDate.of(2007, 1, 13),
                        "1371793"
                ),
                candidate("1371793", "Benjamin Mendy"),
                profile(
                        "1371793",
                        "Benjamin Mendy",
                        LocalDate.of(2007, 1, 13),
                        "Cagliari Calcio"
                )
        );

        assertThat(verification.status())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void knownIdAndBirthDateAcceptShortTransfermarktProfessionalName() {
        var verification = service.verifyProfile(
                player(
                        "Ederson D.S.",
                        "Atalanta",
                        "Ederson Jose dos Santos Lourenco da Silva",
                        LocalDate.of(1999, 7, 7),
                        "607854"
                ),
                candidate("607854", "Ederson da Silva"),
                profile(
                        "607854",
                        "Ederson da Silva",
                        LocalDate.of(1999, 7, 7),
                        "Atalanta BC"
                )
        );

        assertThat(verification.status())
                .isEqualTo("MATCHED_VERIFIED");
    }

    private TransfermarktDiscoveryService.OperationalPlayer player(
            String displayName,
            String club,
            String canonicalName,
            LocalDate birthDate,
            String transfermarktId
    ) {
        return new TransfermarktDiscoveryService.OperationalPlayer(
                1L,
                displayName,
                club,
                canonicalName,
                birthDate,
                transfermarktId
        );
    }

    private TransfermarktCandidate candidate(
            String id,
            String name
    ) {
        return new TransfermarktCandidate(
                id,
                name,
                "https://www.transfermarkt.com/x/profil/spieler/" + id,
                ""
        );
    }

    private TransfermarktProfile profile(
            String id,
            String name,
            LocalDate birthDate,
            String club
    ) {
        return new TransfermarktProfile(
                id,
                name,
                birthDate,
                "Italy",
                180,
                "RIGHT",
                club,
                "https://www.transfermarkt.com/x/profil/spieler/" + id
        );
    }
}
