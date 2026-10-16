import unittest
import pandas as pd

from transfermarkt_identity_match import audit_master, build_identity_csv, normalize, parse_transfermarkt_id, run


def master_row(identifier="10", name="Mario Rossi", team="roma", dob="1990-01-01"):
    return {"transfermarkt_player_id": identifier, "transfermarkt_club_id": "1", "team": team,
            "team_transfermarkt_name": team.title(), "full_name": name, "date_of_birth": dob,
            "age": "36", "nationality": "Italia", "position": "Centrocampista", "height": "1,80 m",
            "player_url": f"https://www.transfermarkt.it/x/profil/spieler/{identifier}",
            "source": "TRANSFERMARKT", "season": "2026/2027", "observed_at": "2026-08-09T00:00:00+00:00"}


class TransfermarktIdentityMatchTest(unittest.TestCase):
    def catalog(self, name="Mario Rossi"):
        return pd.DataFrame([{"player_id": "1", "display_name": name, "normalized_name": normalize(name),
                              "birth_date": "", "current_club": "Roma", "fantasy_role": "C"}])

    def mappings(self, external_id="", external_name="Mario Rossi"):
        rows = [{"player_id": "1", "source": "FANTACALCIO", "external_id": "FC1",
                 "external_name": external_name, "verified": "t"}]
        if external_id:
            rows.append({"player_id": "1", "source": "FANTAMARKT", "external_id": external_id,
                         "external_name": external_name, "verified": "t"})
        return pd.DataFrame(rows)

    def test_id_parser(self):
        self.assertEqual(parse_transfermarkt_id("TRANSFERMARKT-123"), "123")
        self.assertEqual(parse_transfermarkt_id("FM-123"), "")
        self.assertEqual(parse_transfermarkt_id("TRANSFERMARKT-0"), "")

    def test_unicode_normalization(self):
        self.assertEqual(normalize("Khéphren  Thuram"), "khephren thuram")

    def test_stable_id_is_high(self):
        output, safe = run(self.catalog("Rossi"), self.mappings("TRANSFERMARKT-10", "Rossi"),
                           pd.DataFrame([master_row()]), validate_master=False)
        self.assertEqual(len(safe), 1)
        self.assertEqual(output.iloc[0].match_category, "MATCHED_HIGH_BY_STABLE_ID")

    def test_full_name_and_club_unique_is_high(self):
        output, safe = run(self.catalog(), self.mappings(), pd.DataFrame([master_row()]), validate_master=False)
        self.assertEqual(len(safe), 1)
        self.assertEqual(output.iloc[0].match_category, "MATCHED_HIGH_BY_FULL_NAME_CLUB")

    def test_surname_and_club_are_not_high(self):
        output, safe = run(self.catalog("Rossi"), self.mappings("", "Rossi"), pd.DataFrame([master_row()]), validate_master=False)
        self.assertTrue(safe.empty)
        self.assertEqual(output.iloc[0].match_category, "SURNAME_CLUB_NOT_SUFFICIENT")

    def test_alias_and_dob_is_high(self):
        previous = pd.DataFrame([{"player_id": "1", "canonical_full_name": "Mario Rossi",
                                  "birth_date": "1990-01-01"}])
        output, safe = run(self.catalog("Rossi"), self.mappings("", "Rossi"),
                           pd.DataFrame([master_row()]), previous, validate_master=False)
        self.assertEqual(len(safe), 1)
        self.assertEqual(output.iloc[0].match_category, "MATCHED_HIGH_BY_ALIAS_DOB")

    def test_one_id_two_players_is_collision(self):
        catalog = pd.concat([self.catalog("Rossi"), self.catalog("Rossi").assign(player_id="2")])
        mappings = pd.concat([self.mappings("TRANSFERMARKT-10", "Rossi"),
                              self.mappings("TRANSFERMARKT-10", "Rossi").assign(player_id="2")])
        output, safe = run(catalog, mappings, pd.DataFrame([master_row()]), validate_master=False)
        self.assertTrue(safe.empty)
        self.assertTrue((output.match_category == "ONE_ID_MULTIPLE_PLAYERS").all())

    def test_real_master_integrity(self):
        from transfermarkt_identity_match import MASTER
        result = audit_master(pd.read_csv(MASTER, dtype=str, keep_default_na=False))
        self.assertEqual(result["teams"], 20)
        self.assertEqual(result["unique_ids"], 638)

    def test_master_rejects_malformed_dob_and_duplicate_id(self):
        from transfermarkt_identity_match import MASTER
        malformed = pd.read_csv(MASTER, dtype=str, keep_default_na=False)
        malformed.loc[0, "date_of_birth"] = "2000-00-00"
        malformed.loc[1, "transfermarkt_player_id"] = malformed.loc[0, "transfermarkt_player_id"]
        with self.assertRaisesRegex(ValueError, "duplicate-id.*invalid-calendar-date"):
            audit_master(malformed)

    def test_safe_import_contains_high_only(self):
        from tempfile import TemporaryDirectory
        from pathlib import Path
        output, _ = run(self.catalog(), self.mappings(), pd.DataFrame([master_row()]), validate_master=False)
        with TemporaryDirectory() as directory:
            profiles = build_identity_csv(output, Path(directory))
            safe = pd.read_csv(Path(directory) / "player-identity-safe-import.csv")
        self.assertTrue((profiles[profiles.confidence == "HIGH"].player_id.astype(str)
                         == safe.player_id.astype(str)).all())


if __name__ == "__main__":
    unittest.main()
