from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().with_name(
    "review_reconciliation_candidates.py"
)

spec = importlib.util.spec_from_file_location(
    "review_reconciliation_candidates",
    SCRIPT,
)

if spec is None or spec.loader is None:
    raise RuntimeError(
        f"Impossibile caricare {SCRIPT}"
    )

module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class ReviewReconciliationCandidatesTest(
    unittest.TestCase
):

    def row(
        self,
        external_id: str,
        name: str,
        club: str,
        role: str,
        source: str,
    ):
        return module.PlayerRow(
            external_id=external_id,
            name=name,
            normalized_name=module.normalize_text(
                name
            ),
            club=club,
            normalized_club=module.normalize_text(
                club
            ),
            role=role,
            source=source,
        )

    def test_exact_same_club_same_role_is_top_candidate(self):
        fc = self.row(
            "FC-1",
            "Mario Rossi",
            "Roma",
            "C",
            "FANTACALCIO",
        )

        secondary = [
            self.row(
                "GA-1",
                "MARIO ROSSI",
                "Roma",
                "C",
                "GAZZETTA",
            ),
            self.row(
                "GA-2",
                "Mario Rosi",
                "Lazio",
                "D",
                "GAZZETTA",
            ),
        ]

        candidates = module.candidates_for(
            fc,
            secondary,
            [fc],
            5,
        )

        self.assertEqual(
            "GA-1",
            candidates[0].external_id,
        )
        self.assertEqual(
            "VERY_STRONG",
            module.confidence_label(
                candidates
            ),
        )

    def test_sulemana_reverse_uniqueness_is_ambiguous(self):
        fc_k = self.row(
            "FC-K",
            "Sulemana K.",
            "Atalanta",
            "A",
            "FANTACALCIO",
        )

        fc_i = self.row(
            "FC-I",
            "Sulemana I.",
            "Atalanta",
            "C",
            "FANTACALCIO",
        )

        secondary = [
            self.row(
                "GA-1",
                "SULEMANA",
                "Atalanta",
                "A",
                "GAZZETTA",
            ),
            self.row(
                "GA-2",
                "SULEMANA",
                "Cagliari",
                "C",
                "GAZZETTA",
            ),
        ]

        candidates = module.candidates_for(
            fc_k,
            secondary,
            [fc_k, fc_i],
            5,
        )

        self.assertEqual(
            2,
            candidates[0].reverse_conflicts,
        )
        self.assertEqual(
            "AMBIGUOUS",
            module.confidence_label(
                candidates
            ),
        )

    def test_report_does_not_mutate_any_data_source(self):
        fc = self.row(
            "FC-1",
            "Kolo Muani",
            "Juventus",
            "A",
            "FANTACALCIO",
        )

        ga = self.row(
            "GA-1",
            "KOLO MUANI",
            "Juventus",
            "A",
            "GAZZETTA",
        )

        reviews = [
            {
                "id": 10,
                "fantacalcioExternalId": "FC-1",
            }
        ]

        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.csv"

            unresolved, candidate_rows = module.write_report(
                output,
                reviews,
                {"FC-1": fc},
                [fc],
                {"GAZZETTA": [ga]},
                5,
            )

            content = output.read_text(
                encoding="utf-8"
            )

        self.assertEqual(1, unresolved)
        self.assertEqual(1, candidate_rows)
        self.assertIn(
            "VERY_STRONG",
            content,
        )
        self.assertIn(
            "GA-1",
            content,
        )

    def test_missing_fantamarkt_csv_is_optional(self):
        with tempfile.TemporaryDirectory() as directory:
            missing = Path(directory) / "missing.csv"

            rows = module.load_csv(
                missing,
                "FANTAMARKT",
            )

        self.assertEqual([], rows)


if __name__ == "__main__":
    unittest.main(verbosity=2)
