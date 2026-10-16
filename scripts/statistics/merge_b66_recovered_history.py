from __future__ import annotations

import csv
import hashlib
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

DATA = ROOT / "data" / "statistics" / "international"

SOURCE = DATA / "transfermarkt-international-statistics.csv"

CANDIDATES = (
    DATA
    / "b66-no-history-recovery"
    / "b66-recovery-candidates.csv"
)

CLUBS = DATA / "transfermarkt-club-resolution.csv"


HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/151 Safari/537.36"
    ),
    "Accept": "application/json",
}


with SOURCE.open(
    encoding="utf-8-sig",
    newline="",
) as stream:
    existing = list(
        csv.DictReader(stream)
    )

    fieldnames = list(
        existing[0].keys()
    )


with CANDIDATES.open(
    encoding="utf-8-sig",
    newline="",
) as stream:
    candidates = list(
        csv.DictReader(stream)
    )


with CLUBS.open(
    encoding="utf-8-sig",
    newline="",
) as stream:
    club_resolution = {
        row["external_club_id"]: row
        for row in csv.DictReader(stream)
    }


existing_keys = {
    (
        row["transfermarkt_id"],
        row["season_code"],
        row["competition_code"],
        row["external_club_id"],
    )
    for row in existing
}


payload_info = {}


for row in candidates:
    tm_id = row["transfermarkt_id"]

    if tm_id in payload_info:
        continue

    url = (
        "https://tmapi.transfermarkt.technology/"
        f"player/{tm_id}/performance-game"
    )

    request = urllib.request.Request(
        url,
        headers=HEADERS,
    )

    with urllib.request.urlopen(
        request,
        timeout=60,
    ) as response:
        payload = response.read()

    payload_info[tm_id] = {
        "url": url,
        "sha256": hashlib.sha256(
            payload
        ).hexdigest(),
    }


acquired_at = (
    datetime.now(timezone.utc)
    .replace(microsecond=0)
    .isoformat()
)


added = []


for row in candidates:

    key = (
        row["transfermarkt_id"],
        row["season_code"],
        row["competition_code"],
        row["external_club_id"],
    )

    if key in existing_keys:
        continue

    club = club_resolution[
        row["external_club_id"]
    ]

    provenance = payload_info[
        row["transfermarkt_id"]
    ]

    output = {
        field: ""
        for field in fieldnames
    }

    values = {
        "player_id": row["player_id"],
        "transfermarkt_id":
            row["transfermarkt_id"],
        "player_name":
            row["player_name"],
        "season_code":
            row["season_code"],
        "competition_code":
            row["competition_code"],
        "competition_name":
            row["competition_name"],
        "competition_type":
            row["competition_type"],

        # Per competizioni internazionali il vecchio
        # dataset associa la provenienza del club.
        "country":
            row["country"]
            or club["country"],

        "country_code":
            row["country_code"]
            or club["country_code"],

        "external_club_id":
            row["external_club_id"],
        "club_name":
            row["club_name"],

        "appearances":
            row["appearances"],
        "starts":
            row["starts"],
        "minutes_played":
            row["minutes_played"],
        "goals":
            row["goals"],
        "assists":
            row["assists"],
        "yellow_cards":
            row["yellow_cards"],

        # Non inventiamo valori non distinguibili
        # con certezza dal feed corrente.
        "second_yellow_cards": "",
        "red_cards": "",

        "source": "TRANSFERMARKT",
        "source_external_id":
            row["transfermarkt_id"],

        "source_url":
            provenance["url"],

        "source_page_sha256":
            provenance["sha256"],

        "acquired_at_utc":
            acquired_at,
    }

    for field, value in values.items():
        if field in output:
            output[field] = value

    added.append(output)

    existing_keys.add(key)


all_rows = existing + added


with SOURCE.open(
    "w",
    encoding="utf-8",
    newline="",
) as stream:

    writer = csv.DictWriter(
        stream,
        fieldnames=fieldnames,
        quoting=csv.QUOTE_ALL,
    )

    writer.writeheader()
    writer.writerows(all_rows)


print("=== B66 HISTORY MERGE ===")
print(f"existing_before={len(existing)}")
print(f"added={len(added)}")
print(f"total_after={len(all_rows)}")
print(f"players_added={len(set(r['player_id'] for r in added))}")