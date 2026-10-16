package it.fantacalciointelligence.backend.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FantacalcioListoneParserTest {

    private final FantacalcioListoneParser parser =
            new FantacalcioListoneParser();

    @Test
    void shouldParseClassicPlayerRow() {

        String template = """
                <tr class="player-row"
                    data-index="%d"
                    data-filter-keywords="Mastantuono"
                    data-filter-team-id="6"
                    data-filter-playeds="0"
                    data-filter-role-classic="c"
                    data-filter-role-mantra="w|t">

                    <th class="player-role player-role-classic">
                        <span class="role" data-value="c"></span>
                    </th>

                    <th class="player-name">
                        <a class="player-name player-link"
                           href="https://www.fantacalcio.it/serie-a/squadre/fiorentina/mastantuono/%d">
                            <span>Mastantuono</span>
                        </a>
                    </th>

                    <td class="player-team"
                        data-col-key="sq">FIO</td>

                    <td class="player-classic-current-price"
                        data-col-key="c_qa">12</td>

                    <td class="player-classic-fvm"
                        data-col-key="c_fvm">48</td>

                </tr>
                """;

        StringBuilder html = new StringBuilder();

        for (int i = 0; i < 300; i++) {
            html.append(
                    template.formatted(
                            i,
                            7078 + i
                    )
            );
        }

        List<FantacalcioListoneParser.ListonePlayer> players =
                parser.parse(html.toString());

        FantacalcioListoneParser.ListonePlayer first =
                players.getFirst();

        assertEquals(300, players.size());

        assertEquals(
                "FANTACALCIO-7078",
                first.externalId()
        );

        assertEquals(
                "Mastantuono",
                first.name()
        );

        assertEquals(
                "fiorentina",
                first.clubSlug()
        );

        assertEquals(
                "C",
                first.role()
        );

        assertEquals(
                "12",
                first.quotation().toPlainString()
        );

        assertEquals(
                "48",
                first.fmv().toPlainString()
        );

        assertFalse(first.outOfGame());
    }

    @Test
    void shouldDetectFantacalcioOutOfGameMarker() {

        StringBuilder html = new StringBuilder();

        for (int i = 0; i < 300; i++) {

            int id = 10000 + i;

            String marker =
                    i == 0
                            ? """
                            <span class="out-of-game"
                                  title="Non gioca più in Serie A">*</span>
                            """
                            : "";

            String row = """
                    <tr class="player-row"
                        data-index="%d"
                        data-filter-keywords="Djimsiti"
                        data-filter-team-id="1"
                        data-filter-playeds="0"
                        data-filter-role-classic="d"
                        data-filter-role-mantra="dc">

                        <th class="player-role player-role-classic">
                            <span class="role" data-value="d"></span>
                        </th>

                        <th class="player-name">

                            <a class="player-name player-link"
                               href="https://www.fantacalcio.it/serie-a/squadre/atalanta/djimsiti/%d"
                               target="_self">

                                <span>Djimsiti</span>

                            </a>

                            %s

                        </th>

                        <td class="player-team"
                            data-col-key="sq">
                            ATA
                        </td>

                        <td class="player-classic-current-price"
                            data-col-key="c_qa">
                            7
                        </td>

                        <td class="player-classic-fvm"
                            data-col-key="c_fvm">
                            16
                        </td>

                    </tr>
                    """.formatted(
                    i,
                    id,
                    marker
            );

            html.append(row);
        }

        List<FantacalcioListoneParser.ListonePlayer> players =
                parser.parse(html.toString());

        assertEquals(300, players.size());

        FantacalcioListoneParser.ListonePlayer djimsiti =
                players.getFirst();

        assertEquals(
                "FANTACALCIO-10000",
                djimsiti.externalId()
        );

        assertEquals(
                "Djimsiti",
                djimsiti.name()
        );

        assertEquals(
                "atalanta",
                djimsiti.clubSlug()
        );

        assertEquals(
                "D",
                djimsiti.role()
        );

        assertEquals(
                "7",
                djimsiti.quotation().toPlainString()
        );

        assertEquals(
                "16",
                djimsiti.fmv().toPlainString()
        );

        assertTrue(djimsiti.outOfGame());

        assertFalse(
                players.get(1).outOfGame()
        );
    }

    @Test
    void shouldParseNumericHtmlEntityInTourè() {

        StringBuilder html = new StringBuilder();

        for (int i = 0; i < 300; i++) {

            int id = 9000 + i;

            String row = """
                    <tr class="player-row"
                        data-index="%d"
                        data-filter-keywords="Tour&#xE8; E."
                        data-filter-team-id="15"
                        data-filter-playeds="0"
                        data-filter-role-classic="a">

                        <th class="player-name">

                            <a class="player-name player-link"
                               href="https://www.fantacalcio.it/serie-a/squadre/parma/tour&#xE8;-e/%d"
                               target="_self">

                                <span>Tour&#xE8; E.</span>

                            </a>

                        </th>

                        <td class="player-classic-current-price">
                            11
                        </td>

                        <td class="player-classic-fvm">
                            43
                        </td>

                    </tr>
                    """.formatted(i, id);

            html.append(row);
        }

        List<FantacalcioListoneParser.ListonePlayer> players =
                parser.parse(html.toString());

        FantacalcioListoneParser.ListonePlayer first =
                players.getFirst();

        assertEquals(300, players.size());

        assertEquals(
                "FANTACALCIO-9000",
                first.externalId()
        );

        assertEquals(
                "Tourè E.",
                first.name()
        );

        assertEquals(
                "parma",
                first.clubSlug()
        );

        assertEquals(
                "tourè-e",
                first.playerSlug()
        );

        assertEquals(
                "A",
                first.role()
        );

        assertEquals(
                "11",
                first.quotation().toPlainString()
        );

        assertEquals(
                "43",
                first.fmv().toPlainString()
        );

        assertFalse(first.outOfGame());
    }
}