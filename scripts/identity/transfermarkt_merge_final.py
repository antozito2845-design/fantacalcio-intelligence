import csv
from collections import defaultdict
from pathlib import Path

REPO = Path(r"C:\Users\user\OneDrive\Desktop\Fantafoot\fantacalcio-intelligence")

INPUT_DIR = REPO / "data" / "identity" / "transfermarkt-2026-2027"
MASTER = REPO / "data" / "identity" / "transfermarkt-serie-a-2026-2027.csv"
REPORT = REPO / "data" / "identity" / "transfermarkt-serie-a-2026-2027-report.txt"

EXPECTED_TEAMS = {
    "atalanta",
    "bologna",
    "cagliari",
    "como",
    "fiorentina",
    "frosinone",
    "genoa",
    "inter",
    "juventus",
    "lazio",
    "lecce",
    "milan",
    "monza",
    "napoli",
    "parma",
    "roma",
    "sassuolo",
    "torino",
    "udinese",
    "venezia",
}

csv_files = sorted(INPUT_DIR.glob("*.csv"))

print("=== CONTROLLO FINALE TRANSFERMARKT SERIE A 2026/27 ===")
print(f"CSV trovati: {len(csv_files)}")

teams_found = {f.stem.lower() for f in csv_files}

missing = sorted(EXPECTED_TEAMS - teams_found)
unexpected = sorted(teams_found - EXPECTED_TEAMS)

if missing:
    print("MANCANTI:")
    for team in missing:
        print(f"- {team}")

if unexpected:
    print("NON ATTESI:")
    for team in unexpected:
        print(f"- {team}")

if missing:
    raise SystemExit("STOP: mancano uno o piu CSV squadra.")

all_rows = []
fieldnames = None
team_counts = {}

for path in csv_files:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)

        if fieldnames is None:
            fieldnames = reader.fieldnames

        rows = list(reader)

    team_counts[path.stem] = len(rows)
    all_rows.extend(rows)

print("")
print(f"Righe complessive prima deduplica: {len(all_rows)}")

by_id = defaultdict(list)

for row in all_rows:
    player_id = (row.get("transfermarkt_player_id") or "").strip()

    if not player_id:
        raise SystemExit("STOP: trovata riga senza transfermarkt_player_id.")

    by_id[player_id].append(row)

collisions = []
duplicates_same_person = []
unique_rows = []

for player_id, records in by_id.items():

    names = {
        (r.get("full_name") or "").strip().casefold()
        for r in records
        if (r.get("full_name") or "").strip()
    }

    dates = {
        (r.get("date_of_birth") or "").strip()
        for r in records
        if (r.get("date_of_birth") or "").strip()
    }

    nationalities = {
        (r.get("nationality") or "").strip().casefold()
        for r in records
        if (r.get("nationality") or "").strip()
    }

    teams = sorted({
        (r.get("team") or "").strip()
        for r in records
        if (r.get("team") or "").strip()
    })

    if len(names) > 1 or len(dates) > 1:
        collisions.append({
            "player_id": player_id,
            "names": sorted(names),
            "dates": sorted(dates),
            "teams": teams,
        })
        continue

    if len(records) > 1:
        duplicates_same_person.append({
            "player_id": player_id,
            "name": records[0].get("full_name", ""),
            "teams": teams,
        })

    best = records[0].copy()

    # Se lo stesso giocatore compare in più rose, manteniamo
    # un'unica identità ma registriamo tutti i club nel campo team.
    if len(teams) > 1:
        best["team"] = "; ".join(teams)

    unique_rows.append(best)

if collisions:
    print("")
    print("COLLISIONI IDENTITARIE TROVATE:")
    for item in collisions:
        print(item)

    raise SystemExit(
        "STOP: collisioni identitarie. Master CSV non generato."
    )

unique_rows.sort(
    key=lambda r: (
        (r.get("team") or "").casefold(),
        (r.get("full_name") or "").casefold(),
        r.get("transfermarkt_player_id") or "",
    )
)

with MASTER.open("w", encoding="utf-8-sig", newline="") as handle:
    writer = csv.DictWriter(
        handle,
        fieldnames=fieldnames,
    )

    writer.writeheader()
    writer.writerows(unique_rows)

total = len(unique_rows)

with_dob = sum(
    bool((r.get("date_of_birth") or "").strip())
    for r in unique_rows
)

with_nat = sum(
    bool((r.get("nationality") or "").strip())
    for r in unique_rows
)

with_pos = sum(
    bool((r.get("position") or "").strip())
    for r in unique_rows
)

with_height = sum(
    bool((r.get("height") or "").strip())
    for r in unique_rows
)

report = []

report.append("TRANSFERMARKT SERIE A 2026/27 - FINAL DATASET REPORT")
report.append("=" * 65)
report.append("")
report.append(f"Squadre CSV: {len(csv_files)}/20")
report.append(f"Righe complessive: {len(all_rows)}")
report.append(f"Giocatori Transfermarkt unici: {total}")
report.append(f"Con data di nascita: {with_dob}/{total}")
report.append(f"Con nazionalita: {with_nat}/{total}")
report.append(f"Con posizione: {with_pos}/{total}")
report.append(f"Con altezza: {with_height}/{total}")
report.append(f"Collisioni identitarie: {len(collisions)}")
report.append(
    f"Duplicati stesso ID tra piu rose: {len(duplicates_same_person)}"
)

report.append("")
report.append("DETTAGLIO SQUADRE")
report.append("-" * 65)

for team, count in sorted(team_counts.items()):
    report.append(f"{team:<15} {count}")

if duplicates_same_person:
    report.append("")
    report.append("ID PRESENTI IN PIU ROSE")
    report.append("-" * 65)

    for item in duplicates_same_person:
        report.append(
            f"{item['player_id']} | "
            f"{item['name']} | "
            f"{', '.join(item['teams'])}"
        )

REPORT.write_text(
    "\n".join(report),
    encoding="utf-8",
)

print("")
print("=== RISULTATO FINALE ===")
print(f"Squadre:                  {len(csv_files)}/20")
print(f"Righe complessive:        {len(all_rows)}")
print(f"Giocatori ID unici:       {total}")
print(f"Con data di nascita:      {with_dob}")
print(f"Con nazionalita:          {with_nat}")
print(f"Con posizione:            {with_pos}")
print(f"Con altezza:              {with_height}")
print(f"Collisioni identitarie:   {len(collisions)}")
print(
    f"Duplicati multi-rosa:     {len(duplicates_same_person)}"
)

print("")
print("MASTER CSV:")
print(MASTER)

print("")
print("REPORT:")
print(REPORT)

print("")
print("MASTER DATASET CREATO CORRETTAMENTE.")
print("Nessun dato e' stato importato nel database.")