import unittest

from acquire_transfermarkt_international_history import (
    build_coverage,
    competition_type,
    parse_compact_rows,
)


class TransfermarktInternationalHistoryTest(unittest.TestCase):
    def test_parses_real_zero_and_preserves_unavailable_starts(self):
        page = '''<table class="items"><thead><tr><th>Competition</th><th>Minutes played</th></tr></thead>
        <tbody><tr><td>x</td><td><a title="Championship" href="/championship/startseite/wettbewerb/GB2/saison_id/2024">Championship</a></td>
        <td><a title="Watford FC" href="/watford/startseite/verein/1010/saison_id/2024">club</a></td>
        <td>19</td><td>-</td><td>-</td><td>2 / 1 / -</td><td>1.390'</td></tr></tbody></table>'''
        rows = parse_compact_rows(page, {"player_id": "44", "transfermarkt_id": "648032", "player_name": "Abankwah"},
                                  "2024/2025", "url", "now", {}, {"1010": "England"})
        self.assertEqual(1, len(rows))
        self.assertEqual("0", rows[0]["goals"])
        self.assertEqual("", rows[0]["starts"])
        self.assertEqual("1390", rows[0]["minutes_played"])
        self.assertEqual("1", rows[0]["second_yellow_cards"])

    def test_youth_rows_are_excluded(self):
        self.assertEqual("YOUTH", competition_type("Primavera 1", "IJ1", {}))

    def test_goalkeeper_layout_keeps_unreported_assists_null(self):
        page = '''<table class="items"><thead><tr><th>Competition</th><th title="Goals conceded">Goals conceded</th><th title="Clean sheets">Clean sheets</th><th>Minutes played</th></tr></thead>
        <tbody><tr><td>x</td><td><a title="Eredivisie" href="/eredivisie/startseite/wettbewerb/NL1/saison_id/2024">Eredivisie</a></td>
        <td><a title="Club" href="/club/startseite/verein/1/saison_id/2024">club</a></td>
        <td>2</td><td>-</td><td>- / - / -</td><td>3</td><td>1</td><td>180'</td></tr></tbody></table>'''
        row = parse_compact_rows(page, {"player_id": "1", "transfermarkt_id": "2", "player_name": "Keeper"},
                                 "2024/2025", "url", "now", {}, {"1": "Netherlands"})[0]
        self.assertEqual("", row["assists"])
        self.assertEqual("180", row["minutes_played"])

    def test_complete_fantacalcio_is_skipped_and_two_missing_are_classified(self):
        base = {"player_id": "1", "player_name": "P", "current_club": "C", "fantacalcio_id": "F",
                "transfermarkt_id": "10", "has_fantacalcio_2024_25": "YES", "has_fantacalcio_2025_26": "YES"}
        complete = build_coverage([base], [])[0]
        self.assertEqual("COMPLETE_FANTACALCIO", complete["history_resolution_status"])
        empty = dict(base, has_fantacalcio_2024_25="NO", has_fantacalcio_2025_26="NO")
        missing = build_coverage([empty], [])[0]
        self.assertEqual("NO_HISTORICAL_DATA", missing["history_resolution_status"])
        self.assertEqual("2024/2025|2025/2026", missing["historical_seasons_missing"])

    def test_one_season_only_cross_season_and_review_ambiguity(self):
        base = {"player_id": "1", "player_name": "P", "current_club": "C", "fantacalcio_id": "F",
                "transfermarkt_id": "10", "has_fantacalcio_2024_25": "NO", "has_fantacalcio_2025_26": "NO"}
        stat = {"player_id": "1", "season_code": "2024/2025", "competition_type": "DOMESTIC_LEAGUE",
                "appearances": "1", "minutes_played": "1", "competition_code": "GB1",
                "competition_name": "Premier League", "club_name": "Club", "country": "England"}
        one = build_coverage([base], [stat])[0]
        self.assertEqual("ONE_SEASON_ONLY", one["history_resolution_status"])
        ambiguous = dict(stat, country="")
        review = build_coverage([base], [ambiguous])[0]
        self.assertEqual("REVIEW", review["history_resolution_status"])


if __name__ == "__main__":
    unittest.main()
