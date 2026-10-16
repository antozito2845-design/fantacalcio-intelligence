import importlib.util
import io
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "fbref" / "goalkeeper_offline_dry_run.py"
spec = importlib.util.spec_from_file_location("goalkeeper_offline_dry_run", SCRIPT)
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


CATALOG = """player_id,player_name,current_club,nationality,current_role,fbref_external_id
1,Marco Carnesecchi,Atalanta,Italy,P,fbref-1
2,Mario Rossi,Roma,Italy,P,
3,Mario Rossi,Milan,Italy,P,
4,Movimentista,Napoli,Italy,D,
"""

HEADER = """season,external_id,name,club,nationality,position,source_url,observed_at,MP,Starts,Min,GA,Saves,CS,PKsv
"""


def catalog_players():
    players, invalid = module.parse_catalog_rows(io.StringIO(CATALOG))
    assert not invalid
    return players


def records(csv_body, origin="SEASON_TABLE"):
    return module.parse_input_rows(
        io.StringIO(HEADER + csv_body),
        "2025/2026",
        "2026-08-27T12:00:00Z",
        "",
        origin,
    )


class GoalkeeperOfflineDryRunTest(unittest.TestCase):
    def test_exact_external_mapping_is_high_confidence(self):
        mappings, invalid = module.parse_mapping_rows(
            io.StringIO("source,external_id,player_id\nFBREF,fbref-1,1\n")
        )
        self.assertEqual(invalid, [])
        report = module.run_dry_run(
            catalog_players(),
            records("2025/2026,fbref-1,Marco Carnesecchi,Atalanta,Italy,GK,https://fbref.test/p/1,2026-08-27T12:00:00Z,10,9,900,8,20,4,1\n"),
            mappings,
            ["2025/2026"],
        )
        result = report["records"][0]
        self.assertEqual(result["status"], "MATCHED")
        self.assertEqual(result["match_method"], "EXTERNAL_ID_MAPPING")
        self.assertEqual(result["match_confidence"], "HIGH")
        self.assertEqual(result["candidate_player_id"], "1")

    def test_catalog_embedded_external_id_is_used_without_name_fallback(self):
        report = module.run_dry_run(
            catalog_players(),
            records("2025/2026,fbref-1,Nome abbreviato,Atalanta,Italy,GK,https://fbref.test/p/1,2026-08-27T12:00:00Z,1,1,90,0,1,1,0\n"),
            requested_seasons=["2025/2026"],
        )
        result = report["records"][0]
        self.assertEqual(result["status"], "REVIEW")
        self.assertEqual(result["match_method"], "EXTERNAL_ID_MAPPING_CONFLICT")
        self.assertIn("MAPPING_CONFLICT_FULL_NAME", result["reason"])

    def test_ambiguous_name_never_matches_automatically(self):
        report = module.run_dry_run(
            catalog_players(),
            records("2025/2026,fbref-unknown,Mario Rossi,Roma,Italy,GK,https://fbref.test/p/unknown,2026-08-27T12:00:00Z,1,1,90,0,2,1,0\n"),
            requested_seasons=["2025/2026"],
        )
        result = report["records"][0]
        self.assertEqual(result["status"], "REVIEW")
        self.assertEqual(result["match_method"], "AMBIGUOUS_EXACT_NAME")
        self.assertNotEqual(result["match_confidence"], "HIGH")
        self.assertEqual(result["candidate_player_id"], "2")
        self.assertEqual(result["candidate_name"], "Mario Rossi")

    def test_transfer_rows_with_same_external_id_are_aggregated_once(self):
        mappings = {"fbref-2": {"2"}}
        report = module.run_dry_run(
            catalog_players(),
            records(
                "2025/2026,fbref-2,Mario Rossi,Roma,Italy,GK,https://fbref.test/p/2a,2026-08-27T12:00:00Z,10,10,900,5,20,4,1\n"
                "2025/2026,fbref-2,Mario Rossi,Milan,Italy,GK,https://fbref.test/p/2b,2026-08-27T12:00:00Z,8,8,720,3,18,2,0\n"
            ),
            mappings,
            ["2025/2026"],
        )
        self.assertEqual(len(report["records"]), 1)
        result = report["records"][0]
        self.assertEqual(result["status"], "MATCHED")
        self.assertEqual(result["origin"], "MULTI_CLUB_AGGREGATED")
        self.assertEqual(result["minutes_played"], 1620)
        self.assertEqual(result["goals_conceded"], 8)
        self.assertEqual(result["saves"], 38)
        self.assertEqual(result["clean_sheets"], 6)
        self.assertEqual(result["penalties_saved"], 1)

    def test_history_row_matches_even_when_current_season_table_has_no_player(self):
        history = records(
            "2025/2026,fbref-1,Marco Carnesecchi,Atalanta,Italy,GK,https://fbref.test/p/1,2026-08-27T12:00:00Z,30,30,2700,25,80,10,2\n",
            origin="INDIVIDUAL_HISTORY",
        )
        report = module.run_dry_run(
            catalog_players(),
            history,
            {"fbref-1": {"1"}},
            ["2025/2026"],
        )
        result = report["records"][0]
        self.assertEqual(result["status"], "MATCHED")
        self.assertEqual(result["origin"], "INDIVIDUAL_HISTORY")
        coverage = [row for row in report["coverage"] if row["player_id"] == "1"]
        self.assertEqual(coverage[0]["status"], "MATCHED")

    def test_zero_is_preserved_and_blank_is_null(self):
        report = module.run_dry_run(
            catalog_players(),
            records("2025/2026,fbref-1,Marco Carnesecchi,Atalanta,Italy,GK,https://fbref.test/p/1,2026-08-27T12:00:00Z,0,0,,0,-,0,\n"),
            {"fbref-1": {"1"}},
            ["2025/2026"],
        )
        result = report["records"][0]
        self.assertEqual(result["status"], "MATCHED")
        self.assertEqual(result["appearances"], 0)
        self.assertIsNone(result["minutes_played"])
        self.assertEqual(result["goals_conceded"], 0)
        self.assertIsNone(result["saves"])
        self.assertEqual(result["clean_sheets"], 0)
        self.assertIsNone(result["penalties_saved"])

    def test_invalid_row_is_reported_without_becoming_a_match(self):
        report = module.run_dry_run(
            catalog_players(),
            records("2025/2026,,Marco Carnesecchi,Atalanta,Italy,GK,,2026-08-27T12:00:00Z,1,1,90,-1,2,1,0\n"),
            requested_seasons=["2025/2026"],
        )
        result = report["records"][0]
        self.assertEqual(result["status"], "INVALID")
        self.assertIn("MISSING_FBREF_EXTERNAL_ID", result["reason"])
        self.assertIn("MISSING_SOURCE_URL", result["reason"])
        self.assertIn("INVALID_GOALS_CONCEDED", result["reason"])


if __name__ == "__main__":
    unittest.main()
