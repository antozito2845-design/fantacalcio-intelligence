import csv
import json
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

resolution_file = (
    ROOT
    / "data"
    / "statistics"
    / "international"
    / "transfermarkt-club-resolution.csv"
)

CLUBS = {
    "13": ("Spain", "ES"),
    "26": ("Switzerland", "CH"),
    "86": ("Germany", "DE"),
    "322": ("Switzerland", "CH"),
    "1075": ("Portugal", "PT"),
    "11831": ("Argentina", "AR"),
}

headers = {
    "User-Agent": "Mozilla/5.0",
    "Accept": "application/json",
}

query = urllib.parse.urlencode(
    [("ids[]", club_id) for club_id in CLUBS]
)

url = (
    "https://tmapi.transfermarkt.technology/clubs?"
    + query
)

request = urllib.request.Request(
    url,
    headers=headers,
)

with urllib.request.urlopen(
    request,
    timeout=60,
) as response:
    payload = json.load(response)

if not payload.get("success"):
    raise RuntimeError("Transfermarkt clubs API failed")

api_clubs = {}

for club in payload.get("data", []):
    club_id = str(club.get("id", "")).strip()

    if club_id not in CLUBS:
        continue

    name = (
        club.get("name")
        or club.get("officialName")
        or club.get("shortName")
        or ""
    ).strip()

    if not name:
        raise RuntimeError(
            f"Nome club mancante per ID {club_id}"
        )

    api_clubs[club_id] = name

missing_api = set(CLUBS) - set(api_clubs)

if missing_api:
    raise RuntimeError(
        f"Club non restituiti dalla API: {sorted(missing_api)}"
    )

with resolution_file.open(
    encoding="utf-8-sig",
    newline="",
) as stream:
    existing_rows = list(
        csv.DictReader(stream)
    )

existing_ids = {
    row["external_club_id"]
    for row in existing_rows
}

new_rows = []

for club_id, (country, country_code) in CLUBS.items():

    print(
        f"{club_id} -> {api_clubs[club_id]}"
    )

    if club_id in existing_ids:
        continue

    new_rows.append(
        {
            "external_club_id": club_id,
            "club_name": api_clubs[club_id],
            "country": country,
            "country_code": country_code,
            "reuse_current_club_name": "",
            "resolution_status":
                "CREATE_INACTIVE_HISTORY",
        }
    )

all_rows = existing_rows + new_rows

fields = [
    "external_club_id",
    "club_name",
    "country",
    "country_code",
    "reuse_current_club_name",
    "resolution_status",
]

with resolution_file.open(
    "w",
    encoding="utf-8",
    newline="",
) as stream:
    writer = csv.DictWriter(
        stream,
        fieldnames=fields,
        quoting=csv.QUOTE_MINIMAL,
    )

    writer.writeheader()
    writer.writerows(all_rows)

print()
print("=== B66 CLUB RESOLUTION ===")
print(f"api_clubs=6")
print(f"new_resolution_rows={len(new_rows)}")
print(f"total_resolution_rows={len(all_rows)}")