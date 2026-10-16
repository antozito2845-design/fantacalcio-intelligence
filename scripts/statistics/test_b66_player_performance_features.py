import csv
import importlib.util
import json
import subprocess
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

SCRIPT = (
    ROOT
    / "scripts"
    / "statistics"
    / "build_b66_player_performance_features.py"
)

OUTPUT = (
    ROOT
    / "data"
    / "statistics"
    / "features"
    / "b66-player-performance-import.csv"
)

CONTEXT = (
    ROOT
    / "data"
    / "statistics"
    / "features"
    / "current-player-feature-context.csv"
)


spec = importlib.util.spec_from_file_location(
    "b66_performance",
    SCRIPT,
)

performance = importlib.util.module_from_spec(
    spec
)

spec.loader.exec_module(
    performance
)


def read(path):
    with path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        return list(
            csv.DictReader(stream)
        )


class B66FeatureUnitTest(
    unittest.TestCase
):
    def test_per90_requires_positive_minutes(self):
        self.assertEqual(
            performance.per90(2, 180),
            1.0,
        )

        self.assertIsNone(
            performance.per90(2, 0)
        )

        self.assertIsNone(
            performance.per90(None, 90)
        )

    def test_domestic_league_excludes_cups(self):
        league = {
            "competition_type":
                "DOMESTIC_LEAGUE",
            "competition_name":
                "Premier League",
            "appearances": "1",
            "minutes_played": "90",
            "goals": "1",
            "assists": "0",
            "yellow_cards": "0",
            "second_yellow_cards": "0",
            "red_cards": "0",
        }

        cup = dict(league)

        cup[
            "competition_type"
        ] = "DOMESTIC_CUP"

        cup["goals"] = "9"

        result = (
            performance.aggregate_tm_league(
                [league, cup]
            )
        )

        self.assertEqual(
            result["goals"],
            1,
        )

    def test_no_history_features_are_null(self):
        values = (
            performance.empty_feature_values()
        )

        self.assertTrue(
            all(
                value is None
                for value in values.values()
            )
        )


class B66ArtifactContractTest(
    unittest.TestCase
):
    @classmethod
    def setUpClass(cls):
        subprocess.run(
            [
                sys.executable,
                str(SCRIPT),
            ],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )

        cls.context = read(
            CONTEXT
        )

        cls.profiles = read(
            OUTPUT
        )

    def test_exact_live_population(self):
        self.assertEqual(
            len(self.profiles),
            len(self.context),
        )

        self.assertEqual(
            {
                row["player_id"]
                for row in self.profiles
            },
            {
                row["player_id"]
                for row in self.context
            },
        )

    def test_algorithm_version(self):
        self.assertEqual(
            {
                row["algorithm_version"]
                for row in self.profiles
            },
            {"B66_FEATURES_V3"},
        )

    def test_history_contract(self):
        allowed = {
            "TWO_FANTACALCIO",
            "MIXED",
            "TWO_INTERNATIONAL",
            "ONE_SEASON_ONLY",
            "NO_HISTORY",
        }

        for row in self.profiles:
            self.assertIn(
                row["source_mix"],
                allowed,
            )

            self.assertIn(
                int(
                    row[
                        "history_seasons_available"
                    ]
                ),
                (0, 1, 2),
            )

    def test_no_history_contract(self):
        no_history = [
            row
            for row in self.profiles
            if row["source_mix"]
            == "NO_HISTORY"
        ]

        self.assertEqual(
            len(no_history),
            1,
        )

        for row in no_history:
            self.assertEqual(
                row[
                    "history_seasons_available"
                ],
                "0",
            )

            self.assertEqual(
                row[
                    "latest_history_season"
                ],
                "",
            )

            self.assertEqual(
                row[
                    "data_confidence_score"
                ],
                "0.00",
            )

            features = json.loads(
                row[
                    "feature_values_json"
                ]
            )

            self.assertTrue(
                all(
                    value is None
                    for value
                    in features.values()
                )
            )

    def test_all_fingerprints_are_sha256(self):
        for row in self.profiles:
            fingerprint = row[
                "input_fingerprint"
            ]

            self.assertEqual(
                len(fingerprint),
                64,
            )

            int(
                fingerprint,
                16,
            )


if __name__ == "__main__":
    unittest.main()