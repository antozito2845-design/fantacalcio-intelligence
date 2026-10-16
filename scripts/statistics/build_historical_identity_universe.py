"""Build the deduplicated Fantacalcio historical identity/reconciliation artifacts."""
from __future__ import annotations

import argparse
import csv
import json
import re
import unicodedata
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "statistics" / "fantacalcio"


def read(path: Path) -> list[dict[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def write(path: Path, fields: list[str], rows: list[dict[str, str]]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, extrasaction="ignore")
        writer.writeheader(); writer.writerows(rows)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--db-context", type=Path, required=True)
    parser.add_argument("--profiles", type=Path, default=DATA / "historical-fantacalcio-profiles.csv")
    args = parser.parse_args()
    seasons = {
        "2024/2025": read(DATA / "fantacalcio-serie-a-2024-25.csv"),
        "2025/2026": read(DATA / "fantacalcio-serie-a-2025-26.csv"),
    }
    context = read(args.db_context)
    mapping = {r["fantacalcio_player_id"]: r for r in context if r["fantacalcio_player_id"]}
    profiles = {r["fantacalcio_player_id"]: r for r in read(args.profiles)} if args.profiles.exists() else {}
    def norm(value: str) -> str:
        value = unicodedata.normalize("NFKD", value)
        return " ".join(re.sub(r"[^a-z0-9 ]", " ", value.encode("ascii", "ignore").decode().casefold()).split())
    existing_identity = {(norm(r["display_name"]), r["birth_date"]): r["player_id"]
                         for r in context if r["display_name"] and r["birth_date"]}
    grouped: dict[str, list[tuple[str, dict[str, str]]]] = defaultdict(list)
    for season, rows in seasons.items():
        for row in rows:
            grouped[row["external_id"]].append((season, row))

    universe = []
    resolution: dict[str, dict[str, str]] = {}
    for external_id in sorted(grouped, key=lambda value: int(value.rsplit("-", 1)[1])):
        items = grouped[external_id]
        raw_id = external_id.rsplit("-", 1)[1]
        db = mapping.get(external_id)
        profile = profiles.get(external_id)
        if db:
            created_in_block = profile is not None
            resolved = {"player_id": db["player_id"],
                        "method": ("FANTACALCIO_PROFILE_ID_DOB_CREATED"
                                   if created_in_block else "EXACT_EXTERNAL_ID_EXISTING"),
                        "status": "CERTIFIED", "confidence": "HIGH",
                        "notes": ("Historical identity created inactive in Block 48"
                                  if created_in_block else "Existing certified FANTACALCIO mapping")}
        elif profile and profile.get("profile_status") == "VERIFIED":
            collision = existing_identity.get((norm(profile.get("canonical_full_name", "")),
                                               profile.get("birth_date", "")), "")
            if collision:
                resolved = {"player_id": collision, "method": "EXACT_NAME_DOB_EXISTING",
                            "status": "REVIEW", "confidence": "MEDIUM",
                            "notes": "Existing name+DOB requires manual collision review"}
            else:
                resolved = {"player_id": "", "method": "FANTACALCIO_PROFILE_ID_DOB",
                            "status": "CERTIFIED_NEW", "confidence": "HIGH",
                            "notes": "Stable Fantacalcio ID, canonical profile and birth date"}
        else:
            resolved = {"player_id": "", "method": "NONE", "status": "UNMATCHED",
                        "confidence": "NONE", "notes": "Verified identity profile unavailable"}
        resolution[external_id] = resolved
        universe.append({
            "fantacalcio_player_id": external_id,
            "names_seen": "|".join(sorted({r["name"] for _, r in items})),
            "seasons_seen": "|".join(s for s in seasons if any(x[0] == s for x in items)),
            "clubs_seen": "|".join(sorted({r["club"] for _, r in items})),
            "roles_seen": "|".join(sorted({r["historical_role"] for _, r in items})),
            "existing_player_id": db["player_id"] if db else "",
            "existing_mapping": "YES" if db else "NO",
            "resolution_method": resolved["method"], "resolution_status": resolved["status"],
            "confidence": resolved["confidence"], "notes": resolved["notes"],
        })
    fields = list(universe[0])
    write(DATA / "historical-fantacalcio-identity-universe.csv", fields, universe)

    reconciliation = []
    for season, rows in seasons.items():
        for row in rows:
            resolved = resolution[row["external_id"]]
            profile = profiles.get(row["external_id"], {})
            reconciliation.append({
                "season": season, "fantacalcio_player_id": row["external_id"],
                "source_name": row["name"], "source_club": row["club"],
                "historical_role": row["historical_role"],
                "resolved_player_id": resolved["player_id"],
                "resolution_method": resolved["method"], "confidence": resolved["confidence"],
                "status": "CERTIFIED" if resolved["status"].startswith("CERTIFIED") else resolved["status"],
                "evidence": json.dumps({"profile_url": profile.get("profile_url", row["source_url"]),
                                         "birth_date": profile.get("birth_date", ""),
                                         "profile_sha256": profile.get("profile_sha256", "")},
                                        ensure_ascii=False, separators=(",", ":")),
                "notes": resolved["notes"],
            })
    reconciliation.sort(key=lambda r: (r["season"], int(r["fantacalcio_player_id"].rsplit("-", 1)[1])))
    write(DATA / "historical-reconciliation-certified.csv", list(reconciliation[0]), reconciliation)
    counts = defaultdict(int)
    for row in universe: counts[row["resolution_status"]] += 1
    print(f"unique={len(universe)} both={sum('|' in r['seasons_seen'] for r in universe)} "
          + " ".join(f"{key}={value}" for key, value in sorted(counts.items())))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
