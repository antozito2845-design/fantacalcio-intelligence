import csv
import unittest
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "statistics"
SQL = (ROOT / "scripts" / "statistics" / "certified_transfermarkt_international_import.sql").read_text()
MIGRATION = (ROOT / "backend" / "src" / "main" / "resources" / "db" / "migration" /
             "V47__create_international_competition_statistics.sql").read_text()


def read(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


class InternationalArtifactsTest(unittest.TestCase):
    def test_statistics_quality_null_and_cross_competition_contract(self):
        rows = read(DATA / "international" / "transfermarkt-international-statistics.csv")
        self.assertEqual(980, len(rows))
        keys = {(r["transfermarkt_id"], r["season_code"], r["competition_code"], r["external_club_id"])
                for r in rows}
        self.assertEqual(980, len(keys))
        self.assertTrue(all(r["source"] == "TRANSFERMARKT" for r in rows))
        self.assertTrue(all(r["country_code"] and r["competition_code"] for r in rows))
        self.assertTrue(all(r["starts"] == "" for r in rows))
        for row in rows:
            for field in ("appearances", "minutes_played", "goals", "yellow_cards", "second_yellow_cards", "red_cards"):
                self.assertGreaterEqual(int(row[field]), 0)
            if row["assists"]:
                self.assertGreaterEqual(int(row["assists"]), 0)

    def test_clubs_are_deterministic_and_review_free(self):
        clubs = read(DATA / "international" / "transfermarkt-club-resolution.csv")
        self.assertEqual(183, len(clubs))
        self.assertEqual(183, len({r["external_club_id"] for r in clubs}))
        self.assertNotIn("REVIEW", {r["resolution_status"] for r in clubs})
        self.assertEqual(21, sum(r["resolution_status"] == "REUSE_EXISTING_CLUB_EXACT_ALIAS" for r in clubs))
        self.assertEqual(162, sum(r["resolution_status"] == "CREATE_INACTIVE_HISTORY" for r in clubs))

    def test_final_coverage_matrix(self):
        coverage = read(DATA / "current-2026-2027-historical-coverage.csv")
        self.assertEqual(Counter({"COMPLETE_FANTACALCIO": 253, "NEEDS_EXTERNAL_STATS": 233,
                                  "ONE_SEASON_ONLY": 9}),
                         Counter(r["history_resolution_status"] for r in coverage))
        self.assertEqual(0, sum(r["history_resolution_status"] in ("REVIEW", "NO_HISTORICAL_DATA")
                                for r in coverage))

    def test_schema_and_importer_isolate_statistics_from_identity_and_roles(self):
        self.assertIn("player_competition_season_statistics", MIGRATION)
        self.assertNotIn("average_rating", MIGRATION)
        self.assertNotIn("fantasy_average", MIGRATION)
        self.assertNotRegex(SQL, r"INSERT INTO\s+player_source_mappings")
        self.assertNotRegex(SQL, r"INSERT INTO\s+players")
        self.assertNotRegex(SQL, r"INSERT INTO\s+player_roles")
        self.assertIn("active)\nSELECT s.club_name", SQL)
        self.assertIn("s.country_code,FALSE", SQL)
        self.assertLess(SQL.index("\\if :preflight"), SQL.index("INSERT INTO clubs"))


if __name__ == "__main__":
    unittest.main()
