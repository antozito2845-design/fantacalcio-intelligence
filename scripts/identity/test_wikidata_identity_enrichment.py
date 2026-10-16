import unittest

import pandas as pd

from wikidata_identity_enrichment import build_aliases, claim_time, height_cm, normalize


class WikidataIdentityEnrichmentTest(unittest.TestCase):
    def test_partial_date_is_not_importable(self):
        entity = {"claims": {"P569": [{"mainsnak": {"datavalue": {
            "value": {"time": "+2006-00-00T00:00:00Z"}}}}]}}
        self.assertEqual(claim_time(entity, "P569"), "")

    def test_height_metres_is_converted_to_centimetres(self):
        entity = {"claims": {"P2048": [{"mainsnak": {"datavalue": {
            "value": {"amount": "+1.84"}}}}]}}
        self.assertEqual(height_cm(entity), 184)

    def test_only_verified_multi_token_aliases_are_candidates(self):
        catalog = pd.DataFrame([{"player_id": "1", "display_name": "Rossi"}])
        mappings = pd.DataFrame([
            {"player_id": "1", "external_name": "Mario Rossi", "verified": "true"},
            {"player_id": "1", "external_name": "Pietro Rossi", "verified": "false"},
        ])
        aliases = build_aliases(catalog, mappings)
        self.assertEqual({normalize(value) for value in aliases["1"]}, {"mario rossi"})


if __name__ == "__main__":
    unittest.main()
