import unittest

from acquire_fantacalcio_history import StatisticsTableParser, normalize
from acquire_historical_fantacalcio_profiles import canonical_parts


class AcquisitionTest(unittest.TestCase):
    def test_parses_nulls_and_certified_zero(self):
        html = '''<tr class="player-row" data-filter-role-classic="a"><th class="player-name"><a class="player-link" href="https://www.fantacalcio.it/serie-a/squadre/x/rossi/42/2024-25/statistico"><span>Rossi</span></a></th><td data-col-key="sq">ROM</td><td data-col-key="pg">0</td><td data-col-key="mv"></td><td data-col-key="mfv"></td><td data-col-key="gol">0</td><td data-col-key="gs">0</td><td data-col-key="rig">0 / 0</td><td data-col-key="rp">0</td><td data-col-key="ass">0</td><td data-col-key="amm">0</td><td data-col-key="esp">0</td></tr>'''
        parser = StatisticsTableParser(); parser.feed(html)
        row = normalize(parser.rows[0])
        self.assertEqual("FANTACALCIO-42", row["external_id"])
        self.assertEqual("0", row["appearances"])
        self.assertEqual("", row["average_rating"])
        self.assertEqual("0", row["penalties_missed"])

    def test_rejects_impossible_penalties(self):
        with self.assertRaises(ValueError):
            normalize({"external_id": "1", "name": "X", "rig": "2 / 1"})

    def test_canonicalizes_historical_surname_first_name(self):
        self.assertEqual(("Boris Radunovic", "Boris", "Radunovic"),
                         canonical_parts("Radunovic Boris", "Radunovic"))
        self.assertEqual(("Idrissa Tourè", "Idrissa", "Tourè"),
                         canonical_parts("Tourè Idrissa", "Tourè I."))


if __name__ == "__main__":
    unittest.main()
