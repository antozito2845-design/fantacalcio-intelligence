#!/usr/bin/env python3
"""Resolve 2024/25-2025/26 careers and acquire real Transfermarkt statistics.

Identity is never inferred here: every request is keyed by an already-certified
Transfermarkt ID exported in the current-player coverage artifact.
"""

from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import html as html_lib
import json
import re
import time
import urllib.error
import urllib.request
from collections import defaultdict
from pathlib import Path

SEASONS = {"2024/2025": "2024", "2025/2026": "2025"}
SOURCE = "TRANSFERMARKT"
BASE_URL = "https://www.transfermarkt.co.uk/x/leistungsdatendetails/spieler/{player_id}/plus/0?saison={year}&verein=&liga=&wettbewerb=&pos=&trainer_id="
FIELDS = [
    "player_id", "transfermarkt_id", "player_name", "season_code", "competition_code",
    "competition_name", "competition_type", "country", "country_code", "external_club_id",
    "club_name", "appearances", "starts", "minutes_played", "goals", "assists",
    "yellow_cards", "second_yellow_cards", "red_cards", "source", "source_external_id",
    "source_url", "source_page_sha256", "acquired_at_utc",
]

COUNTRY_CODES = {
    "Austria": "AT", "Argentina": "AR", "Australia": "AU", "Belgium": "BE",
    "Brazil": "BR", "Switzerland": "CH", "Spain": "ES", "Germany": "DE",
    "Denmark": "DK", "England": "GB", "France": "FR", "Greece": "GR",
    "Italy": "IT", "Japan": "JP", "Croatia": "HR", "Mexico": "MX",
    "United States": "US", "Netherlands": "NL", "Norway": "NO", "Poland": "PL",
    "Portugal": "PT", "Romania": "RO", "Korea, South": "KR", "Russia": "RU",
    "Saudi Arabia": "SA", "Scotland": "GB", "Sweden": "SE", "Serbia": "RS",
    "Türkiye": "TR", "Czech Republic": "CZ", "Ukraine": "UA", "Ireland": "IE",
    "Spain": "ES", "Slovenia": "SI", "Uruguay": "UY", "Bosnia and Herzegovina": "BA",
    "Bulgaria": "BG", "Colombia": "CO",
}
PREFIX_COUNTRIES = {
    "IT": "Italy", "GB": "England", "ES": "Spain", "FR": "France", "NL": "Netherlands",
    "BE": "Belgium", "PO": "Portugal", "TR": "Türkiye", "GR": "Greece", "DK": "Denmark",
    "IR": "Ireland", "SC": "Scotland", "A": "Austria", "C1": "Switzerland", "C2": "Switzerland",
    "SCC": "Switzerland", "L": "Germany",
    "KR": "Croatia", "PL": "Poland", "RO": "Romania", "RU": "Russia", "SE": "Sweden",
    "SER": "Serbia", "UKR": "Ukraine", "NO": "Norway", "BRA": "Brazil", "ARG": "Argentina",
    "MLS": "United States", "MEX": "Mexico", "SA": "Saudi Arabia", "JAP": "Japan",
    "E3": "Spain", "E4": "Spain", "SL": "Slovenia", "UR": "Uruguay", "BR": "Brazil",
    "RESU": "Sweden", "BIH": "Bosnia and Herzegovina", "BOS": "Bosnia and Herzegovina",
    "BUL": "Bulgaria", "BU": "Bulgaria", "RL": "Germany", "COL": "Colombia",
    "CLPI": "Argentina",
}
YOUTH_MARKERS = ("u19", "u20", "u21", "u23", "youth", "primavera", "junior", "reserve")
EXACT_COMPETITION_COUNTRIES = {"POSB": "Italy", "SCIC": "Italy", "POPU": "Poland"}
INTERNATIONAL_CLUB_COMPETITIONS = {"CL", "CLQ", "EL", "ELQ", "ECLQ", "UCOL", "KLUB", "USC"}
KNOWN_INTERNAL_CLUB_NAMES = {
    "AC Milan": "Milan", "AS Roma": "Roma", "Parma Calcio 1913": "Parma",
    "Genoa CFC": "Genoa", "SS Lazio": "Lazio", "Udinese Calcio": "Udinese",
    "Torino FC": "Torino", "ACF Fiorentina": "Fiorentina", "Juventus FC": "Juventus",
    "Venezia FC": "Venezia", "US Lecce": "Lecce", "Bologna FC 1909": "Bologna",
    "Como 1907": "Como", "Cagliari Calcio": "Cagliari", "AC Monza": "Monza",
    "SSC Napoli": "Napoli", "US Sassuolo": "Sassuolo", "Atalanta BC": "Atalanta",
    "Inter Milan": "Inter", "Frosinone Calcio": "Frosinone",
    "US Cremonese": "Cremonese", "Empoli FC": "Empoli", "Pisa Sporting Club": "Pisa",
    "Hellas Verona": "Verona",
}


def clean_cell(value: str) -> str:
    value = re.sub(r"<[^>]+>", "", value)
    return re.sub(r"\s+", " ", html_lib.unescape(value)).strip()


def number(value: str) -> int:
    value = clean_cell(value).replace(".", "").replace("'", "")
    return 0 if value in ("", "-") else int(value)


def competition_type(name: str, code: str, known: dict[str, dict[str, str]]) -> str:
    if code in INTERNATIONAL_CLUB_COMPETITIONS:
        return "INTERNATIONAL_CUP"
    if code in known:
        return known[code]["type"].upper()
    text = name.lower()
    if any(marker in text for marker in YOUTH_MARKERS):
        return "YOUTH"
    if any(x in text for x in ("champions league", "europa league", "conference league")):
        return "INTERNATIONAL_CUP"
    if any(x in text for x in ("cup", "coppa", "pokal", "beker", "copa")):
        return "DOMESTIC_CUP"
    if "supercup" in text or "super cup" in text or "supercoppa" in text:
        return "OTHER"
    return "DOMESTIC_LEAGUE"


def infer_country(code: str, club_id: str, competitions: dict[str, dict[str, str]],
                  club_countries: dict[str, str]) -> str:
    if code in EXACT_COMPETITION_COUNTRIES:
        return EXACT_COMPETITION_COUNTRIES[code]
    if code in competitions and competitions[code]["country_name"]:
        return competitions[code]["country_name"]
    if club_id in club_countries:
        return club_countries[club_id]
    for prefix in sorted(PREFIX_COUNTRIES, key=len, reverse=True):
        if code.startswith(prefix):
            return PREFIX_COUNTRIES[prefix]
    return ""


def parse_compact_rows(page: str, player: dict[str, str], season_code: str,
                       source_url: str, acquired_at: str,
                       competitions: dict[str, dict[str, str]],
                       club_countries: dict[str, str]) -> list[dict[str, str]]:
    tables = re.findall(r'<table class="items">(.*?)</table>', page, re.S | re.I)
    table = next((t for t in tables if "Competition" in t and "Minutes played" in t), None)
    if table is None:
        return []
    body_match = re.search(r"<tbody>(.*?)</tbody>", table, re.S | re.I)
    if not body_match:
        return []
    digest = hashlib.sha256(page.encode("utf-8")).hexdigest()
    goalkeeper_layout = "Goals conceded" in table and "Clean sheets" in table
    result = []
    for tr in re.findall(r"<tr[^>]*>(.*?)</tr>", body_match.group(1), re.S | re.I):
        cells = re.findall(r"<td[^>]*>(.*?)</td>", tr, re.S | re.I)
        if len(cells) < 8:
            continue
        comp_match = re.search(r'href="/[^"]+/startseite/(?:wettbewerb|pokalwettbewerb)/([^/]+)/saison_id/', tr)
        club_match = re.search(r'href="/[^"]+/startseite/verein/(\d+)/saison_id/', tr)
        comp_name_match = re.search(r'<a title="([^"]+)" href="/[^"]+/startseite/(?:wettbewerb|pokalwettbewerb)/', tr)
        club_name_match = re.search(r'<a title="([^"]+)" href="/[^"]+/startseite/verein/', tr)
        if not (comp_match and club_match and comp_name_match and club_name_match):
            continue
        comp_code, club_id = comp_match.group(1), club_match.group(1)
        comp_name = html_lib.unescape(comp_name_match.group(1))
        club_name = html_lib.unescape(club_name_match.group(1))
        ctype = competition_type(comp_name, comp_code, competitions)
        if ctype == "YOUTH" or any(m in club_name.lower() for m in YOUTH_MARKERS):
            continue
        cards_index = 5 if goalkeeper_layout else 6
        minutes_index = 8 if goalkeeper_layout else 7
        cards = [number(v) for v in clean_cell(cells[cards_index]).split("/")]
        while len(cards) < 3:
            cards.append(0)
        country = infer_country(comp_code, club_id, competitions, club_countries)
        result.append({
            "player_id": player["player_id"], "transfermarkt_id": player["transfermarkt_id"],
            "player_name": player["player_name"], "season_code": season_code,
            "competition_code": comp_code, "competition_name": comp_name,
            "competition_type": ctype, "country": country,
            "country_code": COUNTRY_CODES.get(country, ""), "external_club_id": club_id,
            "club_name": club_name, "appearances": str(number(cells[3])), "starts": "",
            "minutes_played": str(number(cells[minutes_index])), "goals": str(number(cells[4])),
            "assists": "" if goalkeeper_layout else str(number(cells[5])), "yellow_cards": str(cards[0]),
            "second_yellow_cards": str(cards[1]), "red_cards": str(cards[2]),
            "source": SOURCE, "source_external_id": player["transfermarkt_id"],
            "source_url": source_url, "source_page_sha256": digest, "acquired_at_utc": acquired_at,
        })
    return result


def load_reference(cache: Path):
    with gzip.open(cache / "competitions.csv.gz", "rt", encoding="utf-8-sig", newline="") as stream:
        competitions = {r["competition_id"]: r for r in csv.DictReader(stream)}
    club_countries = {}
    with gzip.open(cache / "clubs.csv.gz", "rt", encoding="utf-8-sig", newline="") as stream:
        clubs = list(csv.DictReader(stream))
    for club in clubs:
        comp = competitions.get(club["domestic_competition_id"])
        if comp and comp["country_name"]:
            club_countries[club["club_id"]] = comp["country_name"]
    with gzip.open(cache / "players.csv.gz", "rt", encoding="utf-8-sig", newline="") as stream:
        players = {r["player_id"]: r for r in csv.DictReader(stream)}
    return competitions, club_countries, players


def fetch_page(url: str, destination: Path, delay: float) -> str:
    if destination.exists():
        return destination.read_text(encoding="utf-8")
    request = urllib.request.Request(url, headers={"User-Agent": "FantacalcioIntelligence/1.0 (research data audit)"})
    page = None
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(request, timeout=45) as response:
                if response.status != 200:
                    raise RuntimeError(f"HTTP {response.status}: {url}")
                page = response.read().decode("utf-8", errors="strict")
            break
        except urllib.error.HTTPError as exc:
            if exc.code in (403, 429):
                raise RuntimeError(f"Access/rate-limit stop HTTP {exc.code}: {url}") from exc
            if exc.code < 500 or attempt == 3:
                raise
        except (TimeoutError, urllib.error.URLError):
            if attempt == 3:
                raise
        time.sleep(2 * attempt)
    assert page is not None
    if "leistungsdatendetails" not in page or "Transfermarkt" not in page:
        raise RuntimeError(f"Unexpected response: {url}")
    destination.write_text(page, encoding="utf-8")
    time.sleep(delay)
    return page


def write_csv(path: Path, rows: list[dict[str, str]], fields: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, quoting=csv.QUOTE_ALL)
        writer.writeheader()
        writer.writerows(rows)


def build_coverage(original: list[dict[str, str]], stats: list[dict[str, str]]) -> list[dict[str, str]]:
    by_player_season = defaultdict(list)
    for row in stats:
        by_player_season[(row["player_id"], row["season_code"])].append(row)
    output = []
    fields = [
        "player_id", "player_name", "current_club", "fantacalcio_id", "transfermarkt_id",
        "has_fantacalcio_2024_25", "has_fantacalcio_2025_26", "club_2024_25",
        "competition_2024_25", "country_2024_25", "needs_external_stats_2024_25",
        "club_2025_26", "competition_2025_26", "country_2025_26", "needs_external_stats_2025_26",
        "historical_seasons_missing", "history_resolution_status", "history_resolution_method", "notes",
    ]
    for old in original:
        row = {field: "" for field in fields}
        for field in ("player_id", "player_name", "current_club", "fantacalcio_id", "transfermarkt_id",
                      "has_fantacalcio_2024_25", "has_fantacalcio_2025_26"):
            row[field] = old[field]
        resolved_count = 0
        review = False
        missing = []
        for season_code, suffix, fanta_field in (
            ("2024/2025", "2024_25", "has_fantacalcio_2024_25"),
            ("2025/2026", "2025_26", "has_fantacalcio_2025_26"),
        ):
            candidates = by_player_season.get((old["player_id"], season_code), [])
            candidates.sort(key=lambda x: (
                {"DOMESTIC_LEAGUE": 0, "INTERNATIONAL_CUP": 1, "DOMESTIC_CUP": 2, "OTHER": 3}.get(x["competition_type"], 4),
                -int(x["appearances"]), -int(x["minutes_played"]), x["competition_code"],
            ))
            external_needed = old[fanta_field] == "NO"
            row[f"needs_external_stats_{suffix}"] = "YES" if external_needed else "NO"
            if candidates:
                primary = candidates[0]
                row[f"club_{suffix}"] = primary["club_name"]
                row[f"competition_{suffix}"] = primary["competition_name"]
                row[f"country_{suffix}"] = primary["country"]
                if external_needed and not primary["country"]:
                    review = True
            if old[fanta_field] == "YES" or (external_needed and candidates):
                resolved_count += 1
            else:
                missing.append(season_code)
        row["historical_seasons_missing"] = "|".join(missing)
        if review:
            row["history_resolution_status"] = "REVIEW"
        elif old["has_fantacalcio_2024_25"] == old["has_fantacalcio_2025_26"] == "YES":
            row["history_resolution_status"] = "COMPLETE_FANTACALCIO"
        elif resolved_count == 2:
            row["history_resolution_status"] = "NEEDS_EXTERNAL_STATS"
        elif resolved_count == 1:
            row["history_resolution_status"] = "ONE_SEASON_ONLY"
        else:
            row["history_resolution_status"] = "NO_HISTORICAL_DATA"
        row["history_resolution_method"] = "FANTACALCIO+TRANSFERMARKT_ID_DIRECT"
        row["notes"] = "Career and statistics resolved only by certified Transfermarkt ID; no name matching"
        output.append(row)
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--coverage", type=Path, required=True)
    parser.add_argument("--reference-cache", type=Path, required=True)
    parser.add_argument("--page-cache", type=Path, required=True)
    parser.add_argument("--statistics-output", type=Path, required=True)
    parser.add_argument("--coverage-output", type=Path, required=True)
    parser.add_argument("--clubs-output", type=Path, required=True)
    parser.add_argument("--manifest-output", type=Path, required=True)
    parser.add_argument("--delay", type=float, default=1.0)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    args.page_cache.mkdir(parents=True, exist_ok=True)
    with args.coverage.open(encoding="utf-8-sig", newline="") as stream:
        coverage = list(csv.DictReader(stream))
    targets = [r for r in coverage if r.get("needs_fbref_history") == "YES" or
               r["has_fantacalcio_2024_25"] == "NO" or r["has_fantacalcio_2025_26"] == "NO"]
    if len(targets) != 242 or any(not r["transfermarkt_id"] for r in targets):
        raise RuntimeError(f"Expected 242 targets with certified Transfermarkt ID, got {len(targets)}")
    competitions, club_countries, source_players = load_reference(args.reference_cache)
    acquired_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    stats = []
    for index, player in enumerate(targets, 1):
        for season_code, year in SEASONS.items():
            fanta_key = f"has_fantacalcio_{year}_{str(int(year)+1)[-2:]}"
            if player[fanta_key] == "YES":
                continue
            destination = args.page_cache / f"{player['transfermarkt_id']}-{year}.html"
            url = BASE_URL.format(player_id=player["transfermarkt_id"], year=year)
            if args.offline and not destination.exists():
                raise RuntimeError(f"Missing cached page in offline mode: {destination}")
            page = fetch_page(url, destination, 0 if args.offline else args.delay)
            stats.extend(parse_compact_rows(page, player, season_code, url, acquired_at,
                                            competitions, club_countries))
        if index % 25 == 0:
            print(f"resolved_targets={index}/242 statistics_rows={len(stats)}", flush=True)
    stats.sort(key=lambda r: (int(r["player_id"]), r["season_code"], r["competition_code"], r["external_club_id"]))
    resolved_club_countries = {}
    for row in stats:
        if row["country"]:
            resolved_club_countries.setdefault(row["external_club_id"], row["country"])
    for row in stats:
        if not row["country"] and row["external_club_id"] in resolved_club_countries:
            row["country"] = resolved_club_countries[row["external_club_id"]]
            row["country_code"] = COUNTRY_CODES[row["country"]]
    keys = [(r["player_id"], r["season_code"], r["competition_code"], r["external_club_id"]) for r in stats]
    if len(keys) != len(set(keys)):
        raise RuntimeError("Duplicate international statistics business key")
    invalid = [r for r in stats if not r["country_code"]]
    final_coverage = build_coverage(coverage, stats)
    club_rows = []
    by_club = defaultdict(list)
    for row in stats:
        by_club[row["external_club_id"]].append(row)
    for external_club_id, rows in sorted(by_club.items(), key=lambda item: int(item[0])):
        names = {r["club_name"] for r in rows}
        countries = {(r["country"], r["country_code"]) for r in rows}
        source_name = sorted(names)[0]
        reuse_name = KNOWN_INTERNAL_CLUB_NAMES.get(source_name, "")
        status = "REUSE_EXISTING_CLUB_EXACT_ALIAS" if reuse_name else "CREATE_INACTIVE_HISTORY"
        if len(names) != 1 or len(countries) != 1:
            status = "REVIEW"
        country, country_code = sorted(countries)[0]
        club_rows.append({
            "external_club_id": external_club_id, "club_name": sorted(names)[0],
            "country": country, "country_code": country_code,
            "reuse_current_club_name": reuse_name,
            "resolution_status": status,
        })
    write_csv(args.statistics_output, stats, FIELDS)
    write_csv(args.coverage_output, final_coverage, list(final_coverage[0]))
    write_csv(args.clubs_output, club_rows, list(club_rows[0]))
    manifest = {
        "source": SOURCE,
        "source_dataset": "https://github.com/dcaribou/transfermarkt-datasets",
        "source_license": "CC0-1.0",
        "source_pages": "public Transfermarkt detailed performance pages",
        "acquired_at_utc": acquired_at,
        "target_players": len(targets),
        "statistics_rows": len(stats),
        "unknown_country_rows": len(invalid),
        "clubs": len(club_rows),
        "club_review_rows": sum(r["resolution_status"] == "REVIEW" for r in club_rows),
        "reference_sha256": {
            name: hashlib.sha256((args.reference_cache / name).read_bytes()).hexdigest()
            for name in ("clubs.csv.gz", "competitions.csv.gz", "players.csv.gz")
        },
    }
    args.manifest_output.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(manifest, sort_keys=True))


if __name__ == "__main__":
    main()
