import unittest
import pandas as pd

from fbref_build_dataset import aggregate_table, merge_tables
from fbref_match_catalog import run


class FbrefPipelineTest(unittest.TestCase):
    def catalog(self):
        return pd.DataFrame([
            {"player_id": "1", "display_name": "Mario Rossi", "normalized_name": "mario rossi",
             "current_club": "Napoli", "fantasy_role": "C"},
            {"player_id": "2", "display_name": "Rossi", "normalized_name": "rossi",
             "current_club": "Inter", "fantasy_role": "D"},
            {"player_id": "3", "display_name": "Nuovo", "normalized_name": "nuovo",
             "current_club": "Pisa", "fantasy_role": "A"},
        ])

    def identities(self, player_id="1", name="Mario Rossi", birth_date="1990-01-02"):
        return pd.DataFrame([{
            "player_id": player_id, "canonical_full_name": name,
            "birth_date": birth_date, "identity_status": "VERIFIED_PARTIAL",
            "confidence": "HIGH",
        }])

    def test_multiclub_is_one_player_with_additive_totals_and_recalculated_per90(self):
        source = pd.DataFrame([
            {"external_id": "x", "name": "Mario Rossi", "club": "Roma", "appearances": "10",
             "minutes_played": "900", "goals": "2", "goals_per90": "0.2"},
            {"external_id": "x", "name": "Mario Rossi", "club": "Milan", "appearances": "5",
             "minutes_played": "300", "goals": "1", "goals_per90": "0.3"},
        ])
        result = aggregate_table(source)
        self.assertEqual(len(result), 1)
        self.assertEqual(result.iloc[0].clubs_played, "Roma|Milan")
        self.assertEqual(result.iloc[0].appearances, 15)
        self.assertEqual(result.iloc[0].goals_per90, 0.225)

    def test_surname_only_never_matches_and_current_club_is_not_required(self):
        season = pd.DataFrame([{"external_id": "x", "name": "Lewis Rossi", "clubs_played": "Bologna"}])
        results, _ = run(self.catalog(), {"2025-2026": season})
        self.assertEqual(results["2025-2026"].iloc[0].match_status, "REVIEW")

    def test_same_external_id_propagates_exact_anchor_across_seasons(self):
        old = pd.DataFrame([{"external_id": "x", "name": "M. Rossi", "clubs_played": "Roma"}])
        recent = pd.DataFrame([{"external_id": "x", "name": "Mario Rossi", "born": "1990", "clubs_played": "Bologna"}])
        results, _ = run(self.catalog(), {"2024-2025": old, "2025-2026": recent}, identities=self.identities())
        propagated = results["2024-2025"].iloc[0]
        self.assertEqual(propagated.match_status, "MATCHED")
        self.assertEqual(propagated.match_method, "CROSS_SEASON_EXACT_ANCHOR")
        self.assertEqual(str(propagated.candidate_player_id), "1")

    def test_two_external_ids_cannot_auto_match_same_player(self):
        season = pd.DataFrame([
            {"external_id": "x", "name": "Mario Rossi", "born": "1990", "clubs_played": "Roma"},
            {"external_id": "y", "name": "Mario Rossi", "born": "1990", "clubs_played": "Milan"},
        ])
        results, _ = run(self.catalog(), {"2025-2026": season}, identities=self.identities())
        self.assertTrue((results["2025-2026"].match_status == "REVIEW").all())
        self.assertTrue((results["2025-2026"].match_method == "PLAYER_ID_EXTERNAL_ID_COLLISION").all())

    def test_new_player_without_history_is_not_forced(self):
        season = pd.DataFrame([{"external_id": "x", "name": "Someone Else", "clubs_played": "Roma"}])
        results, coverage = run(self.catalog(), {"2025-2026": season})
        self.assertEqual(results["2025-2026"].iloc[0].match_status, "UNMATCHED")
        nuovo = coverage[coverage.player_id == "3"].iloc[0]
        self.assertIn(nuovo.history_classification, {"NO_HISTORY_EXPECTED", "INSUFFICIENT_MATCHING_DATA"})

    def test_exact_identity_survives_when_player_is_now_abroad(self):
        catalog = self.catalog()
        catalog.loc[catalog.player_id == "1", "current_club"] = "Manchester City"
        season = pd.DataFrame([{"external_id": "x", "name": "Mario Rossi", "born": "1990", "clubs_played": "Roma"}])
        results, _ = run(catalog, {"2024-2025": season}, identities=self.identities())
        self.assertEqual(results["2024-2025"].iloc[0].match_status, "MATCHED")

    def test_full_name_and_birth_date_are_deterministic(self):
        season = pd.DataFrame([{"external_id": "x", "name": "Mario Rossi", "born": "1990",
                                "clubs_played": "Roma"}])
        results, _ = run(self.catalog(), {"2025-2026": season}, identities=self.identities())
        row = results["2025-2026"].iloc[0]
        self.assertEqual(row.match_status, "MATCHED")
        self.assertEqual(row.match_method, "EXACT_FULL_NAME_BIRTH_DATE")

    def test_same_name_different_birth_date_requires_review(self):
        season = pd.DataFrame([{"external_id": "x", "name": "Mario Rossi", "born": "1991",
                                "clubs_played": "Roma"}])
        results, _ = run(self.catalog(), {"2025-2026": season}, identities=self.identities())
        self.assertEqual(results["2025-2026"].iloc[0].match_status, "REVIEW")

    def test_local_tables_merge_without_null_overwrite(self):
        standard = pd.DataFrame([{"external_id": "x", "name": "Mario Rossi", "club": "Roma",
                                  "appearances": "10", "goals": "2"}])
        shooting = pd.DataFrame([{"external_id": "x", "name": "Mario Rossi", "club": "Roma",
                                  "expected_goals": "3.2"}])
        result = merge_tables({"standard": standard, "shooting": shooting})
        self.assertEqual(result.iloc[0].goals, 2)
        self.assertEqual(float(result.iloc[0].expected_goals), 3.2)


if __name__ == "__main__":
    unittest.main()
