import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


class PlayerPerformanceImportContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.migration = (ROOT / "backend/src/main/resources/db/migration/V48__create_player_performance_feature_layer.sql").read_text()
        cls.importer = (ROOT / "scripts/statistics/certified_player_performance_import.sql").read_text()
        cls.runner = (ROOT / "scripts/statistics/run_certified_player_performance_import.ps1").read_text()

    def test_database_enforces_versioned_uniqueness_and_foreign_keys(self):
        self.assertIn("UNIQUE (player_id, reference_season_id, algorithm_version)", self.migration)
        self.assertIn("REFERENCES players(id)", self.migration)
        self.assertIn("REFERENCES player_performance_algorithms(version)", self.migration)

    def test_database_enforces_bounds_and_deferred_score(self):
        self.assertIn("data_confidence_score BETWEEN 0 AND 100", self.migration)
        self.assertIn("ck_player_performance_no_overall_score", self.migration)
        self.assertIn("OVERALL_SCORE_DEFERRED_TO_BLOCK_50", self.importer)

    def test_importer_is_immutable_and_pass2_sequence_safe(self):
        self.assertIn("Immutable performance profile version conflict", self.importer)
        self.assertIn("WHERE NOT EXISTS", self.importer)
        profile_insert = self.importer.split("INSERT INTO player_performance_feature_profiles", 1)[1]
        self.assertNotIn("ON CONFLICT", profile_insert.split("DO $$", 1)[0])

    def test_runner_is_hardened(self):
        self.assertIn("ValidateSet('fantacalcio_intelligence_import_test','fantacalcio_intelligence')", self.runner)
        self.assertIn("Get-NetTCPConnection -LocalPort 8080", self.runner)
        self.assertIn("Remove-Item Env:PGPASSWORD", self.runner)
        self.assertNotIn("Invoke-Expression", self.runner)


if __name__ == "__main__":
    unittest.main()
