package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TransfermarktProfileParserTest {

    private final TransfermarktProfileParser parser =
            new TransfermarktProfileParser();

    @Test
    void parsesCurrentTransfermarktFactsText() {
        String html = """
                <html>
                  <head>
                    <meta property="og:title"
                          content="Raoul Bellanova - Player profile | Transfermarkt">
                  </head>
                  <body>
                    <h1 class="data-header__headline-wrapper">
                        #16 Raoul Bellanova
                    </h1>
                    <span class="data-header__club">
                        Atalanta
                    </span>
                    <div>
                        Facts and data
                        Date of birth/Age: 17/05/2000 (26)
                        Place of birth: Rho
                        Height: 1,88 m
                        Citizenship: Italy
                        Position: Midfield - Right Midfield
                        Foot: right
                        Player agent: Example
                        Current club: Atalanta BC
                        Joined: 22/08/2024
                        Contract expires: 30/06/2029
                    </div>
                  </body>
                </html>
                """;

        TransfermarktProfile profile =
                parser.parse(
                        html,
                        "https://www.transfermarkt.com/raoul-bellanova/profil/spieler/357992"
                );

        assertThat(profile.transfermarktPlayerId())
                .isEqualTo("357992");

        assertThat(profile.fullName())
                .isEqualTo("Raoul Bellanova");

        assertThat(profile.birthDate())
                .isEqualTo(
                        LocalDate.of(
                                2000,
                                5,
                                17
                        )
                );

        assertThat(profile.heightCm())
                .isEqualTo(188);

        assertThat(profile.nationality())
                .isEqualTo("Italy");

        assertThat(profile.preferredFoot())
                .isEqualTo("RIGHT");

        assertThat(profile.currentClub())
                .isEqualTo("Atalanta");
    }

    @Test
    void parsesAhanorStyleCurrentProfile() {
        String html = """
                <html>
                  <body>
                    <h1 class="data-header__headline-wrapper">
                        #69 Honest Ahanor
                    </h1>
                    <span class="data-header__club">
                        Atalanta
                    </span>
                    <div>
                        Date of birth/Age: 23/02/2008 (18)
                        Place of birth: Aversa
                        Height: 1,84 m
                        Citizenship: Italy Nigeria
                        Position: Defender - Centre-Back
                        Foot: left
                        Player agent: GR Sports
                        Current club: Atalanta BC
                        Joined: 04/07/2025
                        Contract expires: 30/06/2028
                    </div>
                  </body>
                </html>
                """;

        TransfermarktProfile profile =
                parser.parse(
                        html,
                        "https://www.transfermarkt.com/honest-ahanor/profil/spieler/1077175"
                );

        assertThat(profile.fullName())
                .isEqualTo("Honest Ahanor");

        assertThat(profile.birthDate())
                .isEqualTo(
                        LocalDate.of(
                                2008,
                                2,
                                23
                        )
                );

        assertThat(profile.heightCm())
                .isEqualTo(184);

        assertThat(profile.preferredFoot())
                .isEqualTo("LEFT");
    }

    @Test
    void missingOptionalFieldsStayNull() {
        String html = """
                <html>
                  <body>
                    <h1 class="data-header__headline-wrapper">
                      Example Player
                    </h1>
                  </body>
                </html>
                """;

        TransfermarktProfile profile =
                parser.parse(
                        html,
                        "https://www.transfermarkt.com/example/profil/spieler/999999"
                );

        assertThat(profile.transfermarktPlayerId())
                .isEqualTo("999999");

        assertThat(profile.birthDate())
                .isNull();

        assertThat(profile.heightCm())
                .isNull();

        assertThat(profile.preferredFoot())
                .isNull();
    }

    @Test
    void citizenshipStopsBeforeHeightField() {
        String html = """
                <html>
                  <body>
                    <h1 class="data-header__headline-wrapper">
                        Honest Ahanor
                    </h1>
                    <span class="data-header__club">Atalanta</span>
                    <div>
                        Date of birth/Age: 23/02/2008 (18)
                        Citizenship: Italy Height: 1,84 m
                        Position: Defender
                        Foot: left
                        Current club: Atalanta
                        Joined: 04/07/2025
                    </div>
                  </body>
                </html>
                """;

        TransfermarktProfile profile =
                parser.parse(
                        html,
                        "https://www.transfermarkt.com/honest-ahanor/profil/spieler/1077175"
                );

        assertThat(profile.nationality())
                .isEqualTo("Italy");

        assertThat(profile.heightCm())
                .isEqualTo(184);
    }
    @Test
    void repairsCommonTransfermarktMojibakeNames() {
        var windows1252 =
                java.nio.charset.Charset.forName(
                        "windows-1252"
                );

        String correctPasalic =
                "Mario Pa\u0161ali\u0107";

        String corruptedPasalic =
                new String(
                        correctPasalic.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8
                        ),
                        windows1252
                );

        assertThat(
                parser.repairMojibake(
                        corruptedPasalic
                )
        ).isEqualTo(
                correctPasalic
        );

        String correctSamardzic =
                "Lazar Samard\u017Ei\u0107";

        String corruptedSamardzic =
                new String(
                        correctSamardzic.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8
                        ),
                        windows1252
                );

        assertThat(
                parser.repairMojibake(
                        corruptedSamardzic
                )
        ).isEqualTo(
                correctSamardzic
        );

        String correctEderson =
                "\u00C9derson";

        String corruptedEderson =
                new String(
                        correctEderson.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8
                        ),
                        windows1252
                );

        assertThat(
                parser.repairMojibake(
                        corruptedEderson
                )
        ).isEqualTo(
                correctEderson
        );
    }
    @Test
    void rejectsGarbageCurrentClubFallback() {
        String html = """
                <html>
                  <body>
                    <h1 class="data-header__headline-wrapper">
                      Gaetano Scirea
                    </h1>
                    <div>
                      Date of birth/Age: 25/05/1953 (36)
                      Citizenship: Italy
                      Height: 1,78 m
                      Foot: right
                      Current club: --- Transfermarkt Videos Youth clubs
                      Atalanta Bergamo Quick Links Most valuable players
                      Legal notice Data protection
                    </div>
                  </body>
                </html>
                """;

        TransfermarktProfile profile =
                parser.parse(
                        html,
                        "https://www.transfermarkt.com/gaetano-scirea/profil/spieler/116740"
                );

        assertThat(
                profile.currentClub()
        ).isNull();
    }}