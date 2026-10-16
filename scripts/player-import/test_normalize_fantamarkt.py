from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().with_name(
    "normalize_fantamarkt.py"
)

spec = importlib.util.spec_from_file_location(
    "normalize_fantamarkt",
    SCRIPT,
)

if spec is None or spec.loader is None:
    raise RuntimeError(
        f"Impossibile caricare {SCRIPT}"
    )

module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class FakeDriver:
    def __init__(self, documents: list[str]):
        self.documents = documents
        self.index = 0
        self.visited: list[str] = []

    def get(self, url: str) -> None:
        self.visited.append(url)
        self.index = 0

    @property
    def page_source(self) -> str:
        document = self.documents[
            min(
                self.index,
                len(self.documents) - 1,
            )
        ]
        self.index += 1
        return document


class NormalizeFantamarktTest(
    unittest.TestCase
):

    def test_parse_squad_page_extracts_real_profile_id_name_and_role(self):
        players = []

        samples = [
            ("101", "Portiere Test", "Portiere"),
            (
                "102",
                "Difensore Test",
                "Difensore centrale",
            ),
            (
                "103",
                "Centrocampista Test",
                "Centrocampista centrale",
            ),
            (
                "104",
                "Attaccante Test",
                "Punta centrale",
            ),
        ]

        for index in range(20):
            player_id, name, position = (
                samples[index % 4]
            )
            unique_id = (
                f"{player_id}{index:02d}"
            )
            unique_name = (
                f"{name} {index}"
            )

            players.append(
                f"""
                <tr class="odd">
                    <td class="hauptlink">
                        <a href="/test/profil/spieler/{unique_id}">
                            {unique_name}
                        </a>
                    </td>
                    <td>{position}</td>
                </tr>
                """
            )

        document = (
            "<html><body><table>"
            + "".join(players)
            + "</table></body></html>"
        )

        rows = module.parse_squad_page(
            document,
            "Juventus",
        )

        self.assertEqual(
            20,
            len(rows),
        )
        self.assertTrue(
            any(
                row.external_id.startswith(
                    "TRANSFERMARKT-"
                )
                for row in rows
            )
        )
        self.assertEqual(
            {"P", "D", "C", "A"},
            {row.role for row in rows},
        )

    def test_discover_teams_requires_all_twenty_clubs(self):
        chunks = []

        aliases = {
            "Atalanta": "Atalanta BC",
            "Bologna": "Bologna FC 1909",
            "Cagliari": "Cagliari Calcio",
            "Como": "Como 1907",
            "Fiorentina": "ACF Fiorentina",
            "Frosinone": "Frosinone Calcio",
            "Genoa": "Genoa CFC",
            "Inter": "Inter Milan",
            "Juventus": "Juventus FC",
            "Lazio": "SS Lazio",
            "Lecce": "US Lecce",
            "Milan": "AC Milan",
            "Monza": "AC Monza",
            "Napoli": "SSC Napoli",
            "Parma": "Parma Calcio 1913",
            "Roma": "AS Roma",
            "Sassuolo": "US Sassuolo",
            "Torino": "Torino FC",
            "Udinese": "Udinese Calcio",
            "Venezia": "Venezia FC",
        }

        for index, club in enumerate(
            sorted(module.EXPECTED_CLUBS),
            start=1,
        ):
            alias = aliases[club]
            chunks.append(
                f"""
                <tr>
                    <td>{alias}</td>
                    <td>
                        <a href="/{club.lower()}/startseite/verein/{index}/saison_id/2026">
                            Rosa
                        </a>
                    </td>
                </tr>
                """
            )

        teams = module.discover_teams(
            "<html>"
            + "".join(chunks)
            + "</html>"
        )

        self.assertEqual(
            20,
            len(teams),
        )
        self.assertEqual(
            module.EXPECTED_CLUBS,
            {
                team.club
                for team in teams
            },
        )
        self.assertTrue(
            all(
                "/kader/verein/"
                in team.squad_url
                for team in teams
            )
        )

    def test_atomic_writer_preserves_expected_csv_contract(self):
        rows = [
            module.PlayerRow(
                external_id=(
                    "TRANSFERMARKT-487969"
                ),
                name="Randal Kolo Muani",
                club="Juventus",
                role="A",
            )
        ]

        with tempfile.TemporaryDirectory() as directory:
            output = (
                Path(directory)
                / "players-fantamarkt-2026-2027.csv"
            )

            module.write_csv_atomic(
                rows,
                output,
            )

            content = output.read_text(
                encoding="utf-8"
            )

        self.assertIn(
            "external_id,name,club,role,quotation,fmv",
            content,
        )
        self.assertIn(
            (
                "TRANSFERMARKT-487969,"
                "Randal Kolo Muani,Juventus,A,,"
            ),
            content,
        )

    def test_failed_run_does_not_overwrite_existing_good_csv(self):
        with tempfile.TemporaryDirectory() as directory:
            directory_path = Path(directory)
            output = (
                directory_path
                / "players-fantamarkt-2026-2027.csv"
            )
            report = (
                directory_path
                / "report.json"
            )

            output.write_text(
                "GOOD_EXISTING_CSV",
                encoding="utf-8",
            )

            original_http_get = module.http_get

            try:
                def fail(
                    _url: str,
                    timeout: int = 30,
                ) -> str:
                    raise module.SourceUnavailable(
                        "HTTP 503"
                    )

                module.http_get = fail

                exit_code = module.run(
                    output_path=output,
                    report_path=report,
                    delay_seconds=0,
                    fetch_mode="http",
                )
            finally:
                module.http_get = (
                    original_http_get
                )

            self.assertEqual(
                1,
                exit_code,
            )
            self.assertEqual(
                "GOOD_EXISTING_CSV",
                output.read_text(
                    encoding="utf-8"
                ),
            )
            self.assertIn(
                '"status": "FAILED"',
                report.read_text(
                    encoding="utf-8"
                ),
            )

    def test_challenge_page_is_detected(self):
        document = """
        <html>
            <body>
                JavaScript is disabled.
                Verify you are human.
            </body>
        </html>
        """

        self.assertTrue(
            module.page_is_challenge(
                document
            )
        )
        self.assertFalse(
            module.page_has_expected_content(
                document,
                "squad",
            )
        )

    def test_browser_waits_until_real_squad_html_arrives(self):
        challenge = """
        <html>
            <body>
                Verify you are human
            </body>
        </html>
        """

        players = []

        for index in range(15):
            players.append(
                f"""
                <tr>
                    <td>
                        <a href="/test/profil/spieler/{9000 + index}">
                            Player {index}
                        </a>
                    </td>
                    <td>Portiere</td>
                </tr>
                """
            )

        real_page = (
            "<html><table>"
            + "".join(players)
            + "</table></html>"
        )

        driver = FakeDriver(
            [
                challenge,
                real_page,
            ]
        )

        original_sleep = module.time.sleep

        try:
            module.time.sleep = (
                lambda _seconds: None
            )

            document = module.browser_get(
                driver,
                "https://example.test/squad",
                content_kind="squad",
                timeout_seconds=2,
                poll_seconds=0.1,
            )
        finally:
            module.time.sleep = (
                original_sleep
            )

        self.assertEqual(
            real_page,
            document,
        )
        self.assertEqual(
            [
                "https://example.test/squad"
            ],
            driver.visited,
        )

    def test_real_transfermarkt_nested_row_exposes_position(self):
        document = """
        <table class="items">
          <tbody>
            <tr class="odd">
              <td class="zentriert rueckennummer bg_Torwart" title="Porta">
                <div class="rn_nummer">29</div>
              </td>
              <td class="posrela">
                <table class="inline-table">
                  <tbody>
                    <tr>
                      <td rowspan="2">
                        <img title="Marco Carnesecchi">
                      </td>
                      <td class="hauptlink">
                        <a href="/marco-carnesecchi/profil/spieler/459763">
                          Marco Carnesecchi
                        </a>
                      </td>
                    </tr>
                    <tr>
                      <td>Portiere</td>
                    </tr>
                  </tbody>
                </table>
              </td>
              <td class="zentriert">26</td>
            </tr>
            <tr class="even">
              <td class="zentriert rueckennummer bg_Abwehr" title="Difesa">
                <div class="rn_nummer">42</div>
              </td>
              <td class="posrela">
                <table class="inline-table">
                  <tbody>
                    <tr>
                      <td rowspan="2">
                        <img title="Giorgio Scalvini">
                      </td>
                      <td class="hauptlink">
                        <a href="/giorgio-scalvini/profil/spieler/571604">
                          Giorgio Scalvini
                        </a>
                      </td>
                    </tr>
                    <tr>
                      <td>Difensore centrale</td>
                    </tr>
                  </tbody>
                </table>
              </td>
              <td class="zentriert">22</td>
            </tr>
          </tbody>
        </table>
        """

        carnesecchi_match = next(
            match
            for match in module.PLAYER_LINK_RE.finditer(document)
            if match.group("id") == "459763"
        )

        context = module.enclosing_row_html(
            document,
            carnesecchi_match.start(),
            carnesecchi_match.end(),
        )

        self.assertIn("Portiere", context)
        self.assertEqual(
            "portiere",
            module.infer_position(
                module.clean_text(context)
            ),
        )

        # Full parser requires >=15 rows, so duplicate a realistic row
        # structure with unique ids/names and mixed positions.
        rows_html = []
        for index in range(20):
            player_id = 700000 + index
            if index % 4 == 0:
                position = "Portiere"
            elif index % 4 == 1:
                position = "Difensore centrale"
            elif index % 4 == 2:
                position = "Centrocampista centrale"
            else:
                position = "Punta centrale"

            parity = "odd" if index % 2 == 0 else "even"

            rows_html.append(
                f"""
                <tr class="{parity}">
                  <td class="zentriert rueckennummer">
                    <div class="rn_nummer">{index}</div>
                  </td>
                  <td class="posrela">
                    <table class="inline-table">
                      <tbody>
                        <tr>
                          <td rowspan="2">
                            <img title="Player {index}">
                          </td>
                          <td class="hauptlink">
                            <a href="/player-{index}/profil/spieler/{player_id}">
                              Player {index}
                            </a>
                          </td>
                        </tr>
                        <tr>
                          <td>{position}</td>
                        </tr>
                      </tbody>
                    </table>
                  </td>
                </tr>
                """
            )

        full_document = (
            "<html><body><table class='items'><tbody>"
            + "".join(rows_html)
            + "</tbody></table></body></html>"
        )

        parsed = module.parse_squad_page(
            full_document,
            "Atalanta",
        )

        self.assertEqual(20, len(parsed))
        self.assertEqual(
            {"P", "D", "C", "A"},
            {row.role for row in parsed},
        )

    def test_cross_club_duplicate_is_quarantined(self):
        rows = [
            module.PlayerRow(
                external_id="TRANSFERMARKT-267160",
                name="Adam Buksa",
                club="Udinese",
                role="A",
            ),
            module.PlayerRow(
                external_id="TRANSFERMARKT-267160",
                name="Adam Buksa",
                club="Altro Club",
                role="A",
            ),
            module.PlayerRow(
                external_id="TRANSFERMARKT-999999",
                name="Player Sicuro",
                club="Udinese",
                role="D",
            ),
        ]

        filtered, conflicts = (
            module.quarantine_cross_club_duplicates(
                rows
            )
        )

        self.assertEqual(
            ["TRANSFERMARKT-999999"],
            [row.external_id for row in filtered],
        )
        self.assertEqual(1, len(conflicts))
        self.assertEqual(
            "TRANSFERMARKT-267160",
            conflicts[0]["external_id"],
        )
        self.assertEqual(
            ["Altro Club", "Udinese"],
            conflicts[0]["clubs"],
        )
        self.assertEqual(
            "QUARANTINED",
            conflicts[0]["action"],
        )

    def test_same_club_duplicate_is_not_cross_club_conflict(self):
        rows = [
            module.PlayerRow(
                external_id="TRANSFERMARKT-123",
                name="Player Test",
                club="Udinese",
                role="A",
            ),
            module.PlayerRow(
                external_id="TRANSFERMARKT-123",
                name="Player Test",
                club="Udinese",
                role="A",
            ),
        ]

        filtered, conflicts = (
            module.quarantine_cross_club_duplicates(
                rows
            )
        )

        self.assertEqual(2, len(filtered))
        self.assertEqual([], conflicts)


    def test_unmapped_position_player_is_reported_not_silently_lost(self):
        rows_html = []

        for index in range(1, 21):
            player_id = 900000 + index
            position = (
                "Posizione Transfermarkt Sconosciuta"
                if index == 20
                else "Central Midfield"
            )

            rows_html.append(
                f"""
                <tr>
                    <td>
                        <a href="/player-{index}/profil/spieler/{player_id}">
                            Player Test {index}
                        </a>
                    </td>
                    <td>{position}</td>
                </tr>
                """
            )

        document = (
            "<html><body><table><tbody>"
            + "".join(rows_html)
            + "</tbody></table></body></html>"
        )

        parsed, rejected = (
            module.parse_squad_page_with_diagnostics(
                document,
                "Torino",
            )
        )

        self.assertEqual(19, len(parsed))
        self.assertEqual(1, len(rejected))

        rejected_player = rejected[0]

        self.assertEqual(
            "TRANSFERMARKT-900020",
            rejected_player.external_id,
        )
        self.assertEqual(
            "Player Test 20",
            rejected_player.name,
        )
        self.assertEqual(
            "Torino",
            rejected_player.club,
        )
        self.assertEqual(
            "POSITION_NOT_MAPPED",
            rejected_player.reason,
        )

    def test_browser_rejects_stale_squad_from_previous_team(self):
        def squad_html(team_id: str, team_name: str) -> str:
            players = "".join(
                (
                    f'<tr><td>'
                    f'<a href="/player-{index}/profil/spieler/{team_id}{index:02d}">'
                    f'Player {index}'
                    f'</a></td>'
                    f'<td>Central Midfield</td></tr>'
                )
                for index in range(1, 16)
            )

            return (
                "<html><head>"
                f'<link rel="canonical" '
                f'href="https://www.transfermarkt.it/test/kader/verein/{team_id}">'
                f"<title>{team_name}</title>"
                "</head><body>"
                + players
                + "</body></html>"
            )

        wrong_html = squad_html(
            "430",
            "Fiorentina",
        )

        correct_html = squad_html(
            "8970",
            "Frosinone",
        )

        class FakeDriver:
            def __init__(self):
                self.calls = 0

            def get(self, url):
                self.url = url

            @property
            def page_source(self):
                self.calls += 1

                if self.calls == 1:
                    return wrong_html

                return correct_html

        driver = FakeDriver()

        result = module.browser_get(
            driver,
            "https://www.transfermarkt.it/frosinone-calcio/kader/verein/8970/saison_id/2026",
            content_kind="squad",
            timeout_seconds=2,
            poll_seconds=0.01,
            expected_team_id="8970",
        )

        self.assertIn(
            "/verein/8970",
            result,
        )

        self.assertNotEqual(
            wrong_html,
            result,
        )

        self.assertGreaterEqual(
            driver.calls,
            2,
        )

if __name__ == "__main__":
    unittest.main(verbosity=2)


