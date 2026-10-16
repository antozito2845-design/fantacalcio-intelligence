import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SQL = (ROOT / "scripts" / "statistics" / "certified_fantacalcio_history_import.sql").read_text(encoding="utf-8")
RUNNER = (ROOT / "scripts" / "statistics" / "run_certified_fantacalcio_history_import.ps1").read_text(encoding="utf-8")


class ImportHardeningContractTest(unittest.TestCase):
    def test_preflight_exits_before_any_sequence_use_or_persistent_insert(self):
        boundary = SQL.index("\\if :preflight")
        self.assertNotIn("nextval", SQL[:boundary].lower())
        self.assertNotRegex(SQL[:boundary], r"INSERT INTO (players|clubs|player_)")
        self.assertLess(boundary, SQL.lower().index("nextval"))

    def test_assignment_is_external_id_to_sequence_id_without_name_reassociation(self):
        self.assertIn("historical_identity_assignment", SQL)
        self.assertRegex(SQL, r"SELECT p\.external_id,nextval\(pg_get_serial_sequence\('players','id'\)\)")
        assignment_block = SQL[SQL.index("CREATE TEMP TABLE historical_identity_assignment"):SQL.index("INSERT INTO player_source_mappings")]
        self.assertNotIn("display_name=p.canonical_full_name", assignment_block)

    def test_complete_conflict_contract_and_runner_configuration(self):
        fields = ("club_id source_external_id appearances starts minutes_played average_rating fantasy_average "
                  "goals assists penalties_scored penalties_missed penalties_taken yellow_cards red_cards "
                  "own_goals goals_conceded clean_sheets saves penalties_saved expected_goals expected_assists").split()
        for field in fields:
            self.assertIn(field, SQL)
        self.assertIn("Conflict with existing historical Fantacalcio role", SQL)
        self.assertIn("Resolve-SpringProperty", RUNNER)
        self.assertIn("SEQUENCES|", (ROOT / "scripts" / "statistics" / "certified_fantacalcio_history_state.sql").read_text())


if __name__ == "__main__":
    unittest.main()
