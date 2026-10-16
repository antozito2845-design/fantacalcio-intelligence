import csv
import hashlib
import importlib.util
import json
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/statistics/build_player_performance_features.py"
spec = importlib.util.spec_from_file_location("performance", SCRIPT)
performance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(performance)


def read(name):
    with (ROOT / "data/statistics/features" / name).open(encoding="utf-8", newline="") as stream:
        return list(csv.DictReader(stream))


def tm_row(kind="DOMESTIC_LEAGUE", name="Premier League", **values):
    row = {key: "0" for key in ("appearances", "minutes_played", "goals", "assists",
                                "yellow_cards", "second_yellow_cards", "red_cards")}
    row.update({"competition_type": kind, "competition_name": name})
    row.update({key: str(value) if value is not None else "" for key, value in values.items()})
    return row


class PlayerPerformanceFeatureUnitTest(unittest.TestCase):
    def test_per90_requires_positive_known_minutes(self):
        self.assertEqual(performance.per90(2, 180), 1.0)
        self.assertIsNone(performance.per90(2, 0))
        self.assertIsNone(performance.per90(None, 90))

    def test_domestic_league_excludes_cups_and_playoffs(self):
        rows = [tm_row(minutes_played=100, goals=1),
                tm_row("DOMESTIC_CUP", "FA Cup", minutes_played=90, goals=9),
                tm_row("DOMESTIC_LEAGUE", "Serie B Play-off", minutes_played=80, goals=8)]
        result = performance.aggregate_tm_league(rows)
        self.assertEqual(result["minutes_played"], 100)
        self.assertEqual(result["goals"], 1)

    def test_multiclub_sum_and_null_assists_are_strict(self):
        known = performance.aggregate_tm_league([tm_row(minutes_played=90, assists=0),
                                                  tm_row(minutes_played=180, assists=2)])
        self.assertEqual(known["minutes_played"], 270)
        self.assertEqual(known["assists"], 2)
        unknown = performance.aggregate_tm_league([tm_row(assists=0), tm_row(assists=None)])
        self.assertIsNone(unknown["assists"])

    def test_percentiles_are_inclusive_and_tie_aware(self):
        ranks = performance.percentile_map([("a", 1), ("b", 2), ("c", 2), ("d", 3)])
        self.assertEqual(ranks["a"], 0)
        self.assertEqual(ranks["b"], 50)
        self.assertEqual(ranks["c"], 50)
        self.assertEqual(ranks["d"], 100)

    def test_quantile_matches_certified_threshold(self):
        audit = read("minutes-distribution-audit.csv")[0]
        self.assertEqual(audit["population"], "ALL")
        self.assertEqual(audit["v1_small_sample_threshold"], "1057")

    def test_source_matrix_separates_pv_and_physical_appearances(self):
        matrix = {(row["source"], row["metric"]): row for row in read("source-metric-availability.csv")}
        self.assertEqual(matrix[("FANTACALCIO", "appearances")]["safe_for_cross_source_feature"], "NO")
        self.assertEqual(matrix[("TRANSFERMARKT", "appearances")]["safe_for_cross_source_feature"], "NO")
        self.assertEqual(matrix[("FANTACALCIO", "goals")]["safe_for_cross_source_feature"], "YES")


class PlayerPerformanceArtifactContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        subprocess.run([sys.executable, str(SCRIPT)], cwd=ROOT, check=True, capture_output=True, text=True)
        cls.profiles = read("player-performance-import.csv")
        cls.bases = read("player-season-performance-base.csv")

    def test_exact_current_population_version_and_roles(self):
        self.assertEqual(len(self.profiles), 495)
        self.assertEqual(len({row["player_id"] for row in self.profiles}), 495)
        self.assertEqual({row["algorithm_version"] for row in self.profiles}, {"B49_FEATURES_V1"})
        self.assertEqual({row["current_role"] for row in self.profiles}, {"P", "D", "C", "A"})

    def test_expected_history_and_source_mix(self):
        self.assertEqual(len(self.bases), 981)
        counts = {mix: sum(row["source_mix"] == mix for row in self.profiles)
                  for mix in {row["source_mix"] for row in self.profiles}}
        self.assertEqual(counts["ONE_SEASON_ONLY"], 9)
        self.assertEqual(counts, {"TWO_FANTACALCIO": 253, "MIXED": 135,
                                  "TWO_INTERNATIONAL": 98, "ONE_SEASON_ONLY": 9})
        self.assertTrue(all(row["history_seasons_available"] == "1"
                            for row in self.profiles if row["source_mix"] == "ONE_SEASON_ONLY"))

    def test_goalkeeper_branch_and_deferred_model(self):
        for row in self.profiles:
            features = json.loads(row["feature_values_json"])
            self.assertIsNone(features["historical_performance_score"])
            self.assertIsNone(features["cross_league_adjustment"])
            if row["current_role"] == "P":
                self.assertIsNone(features["goal_rate_role_percentile"])
                self.assertIsNone(features["assist_rate_role_percentile"])

    def test_small_samples_have_no_rate_percentile(self):
        for row in self.profiles:
            features = json.loads(row["feature_values_json"])
            if row["small_sample_flag"] == "YES":
                self.assertIsNone(features["goal_rate_role_percentile"])
                self.assertIsNone(features["assist_rate_role_percentile"])

    def test_trends_never_cross_source_semantics(self):
        for row in self.profiles:
            features = json.loads(row["feature_values_json"])
            if features["latest_source"] != features["previous_source"]:
                self.assertIsNone(features["delta_goals_per_90"])
                self.assertIsNone(features["delta_assists_per_90"])
                self.assertIsNone(features["fantasy_average_trend"])

    def test_confidence_bounds_and_components(self):
        for row in self.profiles:
            score = float(row["data_confidence_score"])
            evidence = json.loads(row["evidence_json"])
            components = [evidence[key] for key in ("season_coverage_component", "usage_evidence_component",
                          "production_completeness_component", "fantasy_evidence_component")]
            self.assertEqual(score, sum(components) / 4)
            self.assertGreaterEqual(score, 0)
            self.assertLessEqual(score, 100)

    def test_regeneration_is_byte_deterministic(self):
        targets = [ROOT / "data/statistics/features" / name for name in
                   ("player-performance-import.csv", "player-season-performance-base.csv",
                    "player-performance-feature-definitions.csv")]
        before = [hashlib.sha256(path.read_bytes()).hexdigest() for path in targets]
        subprocess.run([sys.executable, str(SCRIPT)], cwd=ROOT, check=True, capture_output=True, text=True)
        after = [hashlib.sha256(path.read_bytes()).hexdigest() for path in targets]
        self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main()
