"""Acquire identity fields from stable public Fantacalcio historical profiles."""
from __future__ import annotations

import argparse
import csv
import hashlib
import html
import re
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "statistics" / "fantacalcio"


def clean(value: str) -> str:
    return " ".join(html.unescape(re.sub(r"<[^>]+>", " ", value)).split())


def extract(pattern: str, page: str) -> str:
    match = re.search(pattern, page, re.I | re.S)
    return clean(match.group(1)) if match else ""


def canonical_parts(profile_name: str, source_name: str) -> tuple[str, str, str]:
    profile_tokens = profile_name.split()
    source_tokens = source_name.split()
    base_tokens = [token for token in source_tokens if not re.fullmatch(r"[A-Za-zÀ-ÿ]\.?", token)]
    if not base_tokens:
        base_tokens = source_tokens[:1]
    prefix = profile_tokens[:len(base_tokens)]
    folded = lambda values: [re.sub(r"\W", "", value).casefold() for value in values]
    if folded(prefix) == folded(base_tokens) and len(profile_tokens) > len(base_tokens):
        first = " ".join(profile_tokens[len(base_tokens):])
        last = " ".join(base_tokens)
        return f"{first} {last}", first, last
    return profile_name, "", profile_name


def acquire(item: dict[str, str]) -> dict[str, str]:
    url = item["source_url"]
    last = None
    for attempt in range(3):
        try:
            safe_url = quote(url, safe=":/?=&%")
            body = urlopen(Request(safe_url, headers={"User-Agent": "FantacalcioIntelligence/1.0"}), timeout=45).read()
            page = body.decode("utf-8", "replace")
            bridge = extract(r"playerId:\s*(\d+)", page)
            expected = item["external_id"].rsplit("-", 1)[1]
            full_name = extract(r'<h1[^>]*class="[^"]*player-name[^"]*"[^>]*>(.*?)</h1>', page)
            birth_date = extract(r'<meta[^>]*itemprop="birthdate"[^>]*content="([^"]+)"', page)
            club = extract(r'<span[^>]*class="team".*?<meta[^>]*itemprop="name"[^>]*content="([^"]+)"', page)
            nationality = extract(r'<dd[^>]*class="[^"]*nationalities[^"]*"[^>]*>(.*?)</dd>', page)
            height = extract(r'<dd[^>]*itemprop="height"[^>]*>(.*?)</dd>', page)
            role = extract(r'class="[^"]*\brole\b[^"]*"[^>]*data-value="([pdca])"', page).upper()
            valid = bool(full_name and birth_date and bridge == expected and role in {"P", "D", "C", "A"})
            return {"fantacalcio_player_id": item["external_id"], "source_name": item["name"],
                    "profile_full_name": full_name, "birth_date": birth_date, "club": club,
                    "fantasy_role": role, "nationality": nationality, "height": height,
                    "profile_url": url, "profile_sha256": hashlib.sha256(body).hexdigest(),
                    "profile_status": "VERIFIED" if valid else "REVIEW", "collision_player_id": "",
                    "notes": "" if valid else "Missing field or profile/URL ID mismatch"}
        except Exception as exc:
            last = exc; time.sleep(attempt + 1)
    return {"fantacalcio_player_id": item["external_id"], "source_name": item["name"],
            "profile_full_name": "", "birth_date": "", "club": "", "fantasy_role": "",
            "nationality": "", "height": "", "profile_url": url, "profile_sha256": "",
            "profile_status": "UNMATCHED", "collision_player_id": "", "notes": str(last)}


def main() -> int:
    parser = argparse.ArgumentParser(); parser.add_argument("--workers", type=int, default=6)
    parser.add_argument("--existing-mappings", type=Path, required=True); args = parser.parse_args()
    mapped = {r["fantacalcio_player_id"] for r in csv.DictReader(args.existing_mappings.open(encoding="utf-8-sig"))}
    output_path = DATA / "historical-fantacalcio-profiles.csv"
    existing = {}
    if output_path.exists():
        with output_path.open(encoding="utf-8", newline="") as handle:
            existing = {r["fantacalcio_player_id"]: r for r in csv.DictReader(handle)
                        if r["profile_status"] == "VERIFIED"}
    targets = {}
    for name in ("fantacalcio-serie-a-2024-25.csv", "fantacalcio-serie-a-2025-26.csv"):
        with (DATA / name).open(encoding="utf-8", newline="") as handle:
            for row in csv.DictReader(handle):
                if row["external_id"] not in mapped and row["external_id"] not in existing:
                    targets.setdefault(row["external_id"], row)
    rows = list(existing.values())
    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(acquire, row): key for key, row in targets.items()}
        for index, future in enumerate(as_completed(futures), 1):
            rows.append(future.result())
            if index % 50 == 0: print(f"profiles={index}/{len(targets)}")
    rows.sort(key=lambda r: int(r["fantacalcio_player_id"].rsplit("-", 1)[1]))
    for row in rows:
        canonical, first, last = canonical_parts(row["profile_full_name"], row["source_name"])
        row["canonical_full_name"] = canonical
        row["first_name"] = first
        row["last_name"] = last
    with output_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)
    print(f"targets={len(rows)} verified={sum(r['profile_status']=='VERIFIED' for r in rows)} "
          f"review={sum(r['profile_status']=='REVIEW' for r in rows)} unmatched={sum(r['profile_status']=='UNMATCHED' for r in rows)}")
    return 0


if __name__ == "__main__": raise SystemExit(main())
