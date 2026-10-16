import csv
import hashlib
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "statistics"


def rows(path):
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


class HistoricalArtifactsTest(unittest.TestCase):
    def test_identity_universe_is_deduplicated_and_certified(self):
        universe = rows(DATA / "fantacalcio" / "historical-fantacalcio-identity-universe.csv")
        self.assertEqual(907, len(universe))
        self.assertEqual(907, len({row["fantacalcio_player_id"] for row in universe}))
        self.assertEqual(485, sum(row["resolution_method"] == "FANTACALCIO_PROFILE_ID_DOB_CREATED"
                                  for row in universe))
        self.assertTrue(all(row["resolution_status"] == "CERTIFIED" for row in universe))

    def test_reconciliation_has_only_resolved_certified_rows(self):
        reconciliation = rows(DATA / "fantacalcio" / "historical-reconciliation-certified.csv")
        self.assertEqual(1342, len(reconciliation))
        self.assertTrue(all(row["status"] == "CERTIFIED" and row["resolved_player_id"]
                            for row in reconciliation))
        self.assertEqual(679, sum(row["season"] == "2024/2025" for row in reconciliation))
        self.assertEqual(663, sum(row["season"] == "2025/2026" for row in reconciliation))

    def test_source_hashes_and_current_coverage(self):
        expected = {
            "fantacalcio-serie-a-2024-25-source.html": "e39b3237a8298512fd3d5a876783ad91b0a4dfdecf3771aa0790b6f03e33d7ca",
            "fantacalcio-serie-a-2025-26-source.html": "12f92ffa41769e99b4cf81d7c39280935a0e18be9f3daf2111c1c86e4de44a52",
        }
        for name, digest in expected.items():
            self.assertEqual(digest, hashlib.sha256((DATA / "fantacalcio" / name).read_bytes()).hexdigest())
        coverage = rows(DATA / "current-2026-2027-historical-coverage.csv")
        self.assertEqual(495, len(coverage))
        self.assertEqual(242, sum(row["has_fantacalcio_2024_25"] == "NO" or
                                  row["has_fantacalcio_2025_26"] == "NO" for row in coverage))


if __name__ == "__main__":
    unittest.main()
