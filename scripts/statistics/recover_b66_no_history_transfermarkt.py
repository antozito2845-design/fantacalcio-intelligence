from __future__ import annotations

import hashlib
import json
import time
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

OUT = (
    ROOT
    / "data"
    / "statistics"
    / "international"
    / "b66-no-history-recovery"
)

OUT.mkdir(
    parents=True,
    exist_ok=True,
)

PLAYERS = [
    (1032, "Molina N.", "424042", "nahuel-molina"),
    (1033, "Kevin Carlos", "718201", "kevin-carlos"),
    (1034, "Romero D.", "668512", "david-romero"),
    (1035, "Schmid", "346853", "romano-schmid"),
    (1036, "Grillitsch", "195736", "florian-grillitsch"),
    (1037, "Terzic", "404260", "aleksa-terzic"),
    (1038, "Aurelio", "457444", "giuseppe-aurelio"),
    (1039, "Penev", "1084756", "plamen-penev"),
    (1111, "Spence", "483348", "djed-spence"),
]

SEASONS = [
    ("2024/2025", "2024"),
    ("2025/2026", "2025"),
]

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/151 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}

manifest = []


def download(url: str) -> bytes:
    request = urllib.request.Request(
        url,
        headers=HEADERS,
    )

    with urllib.request.urlopen(
        request,
        timeout=30,
    ) as response:
        return response.read()


for player_id, name, tm_id, slug in PLAYERS:
    for season_code, season_id in SEASONS:

        url = (
            f"https://www.transfermarkt.com/"
            f"{slug}/leistungsdaten/"
            f"spieler/{tm_id}/"
            f"saison/{season_id}"
        )

        filename = (
            f"{player_id}-"
            f"{tm_id}-"
            f"{season_id}.html"
        )

        path = OUT / filename

        print(
            f"RECOVERY {name} "
            f"{season_code}..."
        )

        try:
            payload = download(url)

            path.write_bytes(payload)

            sha256 = hashlib.sha256(
                payload
            ).hexdigest()

            manifest.append(
                {
                    "player_id": player_id,
                    "player_name": name,
                    "transfermarkt_id": tm_id,
                    "season": season_code,
                    "url": url,
                    "status": "DOWNLOADED",
                    "bytes": len(payload),
                    "sha256": sha256,
                    "file": str(path),
                }
            )

            print(
                f"  OK {len(payload)} bytes"
            )

        except Exception as exc:
            manifest.append(
                {
                    "player_id": player_id,
                    "player_name": name,
                    "transfermarkt_id": tm_id,
                    "season": season_code,
                    "url": url,
                    "status": "FAILED",
                    "error": str(exc),
                }
            )

            print(
                f"  FAILED: {exc}"
            )

        time.sleep(2)


manifest_path = (
    OUT / "recovery-manifest.json"
)

manifest_path.write_text(
    json.dumps(
        manifest,
        indent=2,
        ensure_ascii=False,
    ),
    encoding="utf-8",
)

downloaded = sum(
    row["status"] == "DOWNLOADED"
    for row in manifest
)

failed = sum(
    row["status"] == "FAILED"
    for row in manifest
)

print()
print("=== B66 RECOVERY DOWNLOAD ===")
print(f"requested={len(manifest)}")
print(f"downloaded={downloaded}")
print(f"failed={failed}")
print(f"manifest={manifest_path}")