package it.fantacalciointelligence.backend.identity.enrichment.transfermarkt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TransfermarktSearchParserTest {

    private final TransfermarktSearchParser parser =
            new TransfermarktSearchParser();

    @Test
    void extractsPlayerCandidatesAndDeduplicatesIds() {
        String html = """
                <html>
                  <body>
                    <table>
                      <tr>
                        <td>
                          <a title="Mario Rossi"
                             href="/mario-rossi/profil/spieler/123456">
                             Mario Rossi
                          </a>
                        </td>
                        <td>Atalanta BC</td>
                      </tr>
                      <tr>
                        <td>
                          <a href="/mario-rossi/profil/spieler/123456">
                             Mario Rossi
                          </a>
                        </td>
                      </tr>
                    </table>
                  </body>
                </html>
                """;

        var result =
                parser.parse(
                        html,
                        "https://www.transfermarkt.com/search"
                );

        assertThat(result)
                .hasSize(1);

        assertThat(result.getFirst()
                .transfermarktPlayerId())
                .isEqualTo("123456");

        assertThat(result.getFirst().name())
                .isEqualTo("Mario Rossi");

        assertThat(result.getFirst().url())
                .contains(
                        "/profil/spieler/123456"
                );

        assertThat(result.getFirst().context())
                .contains("Atalanta BC");
    }

    @Test
    void ignoresNonPlayerLinks() {
        String html = """
                <a href="/atalanta/startseite/verein/800">
                    Atalanta
                </a>
                """;

        assertThat(
                parser.parse(
                        html,
                        "https://www.transfermarkt.com/"
                )
        ).isEmpty();
    }
}