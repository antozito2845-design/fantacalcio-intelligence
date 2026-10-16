#!/usr/bin/env python3
import csv
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "statistics"


def read(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def write(path, rows, fields):
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, quoting=csv.QUOTE_ALL)
        writer.writeheader()
        writer.writerows(rows)


stats = read(DATA / "international" / "transfermarkt-international-statistics.csv")
coverage = read(DATA / "current-2026-2027-historical-coverage.csv")
external = {(r["player_id"], r["season_code"]) for r in stats}

matrix = defaultdict(list)
for row in coverage:
    fanta = sum(row[f"has_fantacalcio_{suffix}"] == "YES" for suffix in ("2024_25", "2025_26"))
    ext = sum((row["player_id"], season) in external for season in ("2024/2025", "2025/2026"))
    total = sum(row[f"has_fantacalcio_{suffix}"] == "YES" or (row["player_id"], season) in external
                for suffix, season in (("2024_25", "2024/2025"), ("2025_26", "2025/2026")))
    if row["history_resolution_status"] == "REVIEW": category = "F_REVIEW"
    elif total == 0: category = "E_NO_HISTORICAL_DATA"
    elif total == 1: category = "D_ONE_TOTAL_MEANINGFUL_SEASON"
    elif fanta == 2: category = "A_TWO_FANTACALCIO"
    elif fanta == 1 and ext >= 1: category = "B_ONE_FANTACALCIO_ONE_INTERNATIONAL"
    else: category = "C_TWO_INTERNATIONAL"
    matrix[category].append(row["player_id"])

matrix_order = (
    "A_TWO_FANTACALCIO", "B_ONE_FANTACALCIO_ONE_INTERNATIONAL", "C_TWO_INTERNATIONAL",
    "D_ONE_TOTAL_MEANINGFUL_SEASON", "E_NO_HISTORICAL_DATA", "F_REVIEW",
)
write(DATA / "international" / "international-final-coverage-matrix.csv",
      [{"category": key, "players": len(matrix[key]), "player_ids": "|".join(matrix[key])} for key in matrix_order],
      ["category", "players", "player_ids"])

groups = defaultdict(list)
for row in stats:
    groups[(row["season_code"], row["competition_code"], row["competition_name"], row["country"], row["source"])].append(row)
distribution = []
for key, rows in sorted(groups.items()):
    distribution.append({
        "season": key[0], "competition_code": key[1], "competition": key[2], "country": key[3], "source": key[4],
        "statistics_rows": len(rows), "players": len({r["player_id"] for r in rows}),
        "appearances": sum(int(r["appearances"]) for r in rows),
        "minutes_played": sum(int(r["minutes_played"]) for r in rows),
        "goals": sum(int(r["goals"]) for r in rows),
        "assists_known": sum(int(r["assists"]) for r in rows if r["assists"]),
        "assists_null_rows": sum(not r["assists"] for r in rows),
    })
write(DATA / "international" / "international-competition-distribution.csv", distribution,
      ["season", "competition_code", "competition", "country", "source", "statistics_rows", "players",
       "appearances", "minutes_played", "goals", "assists_known", "assists_null_rows"])

print(f"matrix={[(k, len(matrix[k])) for k in matrix_order]}")
print(f"competition_distribution_rows={len(distribution)}")
