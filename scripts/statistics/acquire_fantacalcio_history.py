"""Acquire public Fantacalcio player-season statistics without authentication.

The source HTML is retained verbatim and the normalized CSV is deterministic.
Only fields explicitly published in the statistics table are emitted; an empty
cell remains empty and is never converted to zero.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import re
from html.parser import HTMLParser
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
SEASONS = ("2024-25", "2025-26")
BASE_URL = "https://www.fantacalcio.it/statistiche-serie-a/{season}/statistico/medie"
CLUB_NAMES = {
    "ATA": "Atalanta", "BOL": "Bologna", "CAG": "Cagliari", "COM": "Como",
    "CRE": "Cremonese", "EMP": "Empoli", "FIO": "Fiorentina", "GEN": "Genoa",
    "INT": "Inter", "JUV": "Juventus", "LAZ": "Lazio", "LEC": "Lecce",
    "MIL": "Milan", "MON": "Monza", "NAP": "Napoli", "PAR": "Parma",
    "PIS": "Pisa", "ROM": "Roma", "SAS": "Sassuolo", "TOR": "Torino",
    "UDI": "Udinese", "VEN": "Venezia", "VER": "Verona",
}


class StatisticsTableParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.rows: list[dict[str, str]] = []
        self.row: dict[str, str] | None = None
        self.key: str | None = None
        self.link: str | None = None
        self.text: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if tag == "tr" and "player-row" in (values.get("class") or ""):
            self.row = {"role": values.get("data-filter-role-classic") or ""}
        if self.row is None:
            return
        if tag in ("th", "td"):
            self.key = values.get("data-col-key")
            classes = values.get("class") or ""
            if "player-name" in classes:
                self.key = "name"
            self.text = []
        elif tag == "a" and "player-link" in (values.get("class") or ""):
            self.link = values.get("href")

    def handle_data(self, data: str) -> None:
        if self.row is not None and self.key:
            self.text.append(data)

    def handle_endtag(self, tag: str) -> None:
        if self.row is None:
            return
        if tag in ("th", "td") and self.key:
            self.row[self.key] = " ".join(" ".join(self.text).split())
            self.key = None
            self.text = []
        elif tag == "tr":
            if self.link:
                match = re.search(r"/([^/]+)/(?P<id>\d+)/\d{4}-\d{2}/", self.link)
                if match:
                    self.row["external_id"] = "FANTACALCIO-" + match.group("id")
                    self.row["source_url"] = self.link
            if self.row.get("external_id") and self.row.get("name"):
                self.rows.append(self.row)
            self.row = None
            self.link = None


def integer(value: str) -> str:
    value = value.strip()
    if not value:
        return ""
    if not re.fullmatch(r"\d+", value):
        raise ValueError(f"Intero Fantacalcio non valido: {value!r}")
    return value


def decimal(value: str) -> str:
    value = value.strip().replace(",", ".")
    if not value:
        return ""
    if not re.fullmatch(r"\d+(?:\.\d+)?", value):
        raise ValueError(f"Decimale Fantacalcio non valido: {value!r}")
    return value


def normalize(row: dict[str, str]) -> dict[str, str]:
    penalties = [part.strip() for part in row.get("rig", "").split("/")]
    if len(penalties) != 2:
        penalties = ["", ""]
    scored, taken = map(integer, penalties)
    missed = str(int(taken) - int(scored)) if scored and taken else ""
    if scored and taken and int(scored) > int(taken):
        raise ValueError(f"Rigori segnati > tirati per {row['name']}")
    return {
        "external_id": row["external_id"], "name": row["name"],
        "club": CLUB_NAMES.get(row.get("sq", ""), row.get("sq", "")),
        "historical_role": row.get("role", "").upper(),
        "appearances": integer(row.get("pg", "")), "starts": "", "minutes_played": "",
        "average_rating": decimal(row.get("mv", "")),
        "fantasy_average": decimal(row.get("mfv", "")),
        "goals": integer(row.get("gol", "")), "assists": integer(row.get("ass", "")),
        "penalties_taken": taken, "penalties_scored": scored, "penalties_missed": missed,
        "yellow_cards": integer(row.get("amm", "")), "red_cards": integer(row.get("esp", "")),
        "own_goals": "", "goals_conceded": integer(row.get("gs", "")),
        "clean_sheets": "", "saves": "", "penalties_saved": integer(row.get("rp", "")),
        "expected_goals": "", "expected_assists": "", "source_url": row.get("source_url", ""),
    }


def acquire(season: str, output: Path) -> tuple[int, str]:
    url = BASE_URL.format(season=season)
    raw = output / f"fantacalcio-serie-a-{season}-source.html"
    if raw.exists():
        body = raw.read_bytes()
    else:
        body = urlopen(Request(url, headers={"User-Agent": "FantacalcioIntelligence/1.0"}), timeout=60).read()
        raw.write_bytes(body)
    parser = StatisticsTableParser()
    parser.feed(body.decode("utf-8", "strict"))
    rows = sorted((normalize(row) for row in parser.rows), key=lambda r: int(r["external_id"].split("-")[-1]))
    if len(rows) < 400:
        raise ValueError(f"Dataset incompleto per {season}: solo {len(rows)} righe")
    ids = [row["external_id"] for row in rows]
    if len(ids) != len(set(ids)):
        raise ValueError(f"external_id duplicati per {season}")
    csv_path = output / f"fantacalcio-serie-a-{season}.csv"
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader(); writer.writerows(rows)
    return len(rows), hashlib.sha256(body).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=ROOT / "data" / "statistics" / "fantacalcio")
    args = parser.parse_args(); args.output.mkdir(parents=True, exist_ok=True)
    for season in SEASONS:
        count, digest = acquire(season, args.output)
        print(f"{season}: rows={count} source_sha256={digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
