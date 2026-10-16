import csv
import re
import sys
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup


# ============================================================
# CONFIGURAZIONE
# ============================================================

SEASON = "2026"
SEASON_LABEL = "2026-2027"

BASE_URL = "https://www.transfermarkt.it"

COMPETITION_URL = (
    "https://www.transfermarkt.it/serie-a/startseite/"
    "wettbewerb/IT1/plus/?saison_id=2026"
)

REPO = Path(
    r"C:\Users\user\OneDrive\Desktop\Fantafoot\fantacalcio-intelligence"
)

OUTPUT_DIR = (
    REPO
    / "data"
    / "identity"
    / "transfermarkt-2026-2027"
)

MASTER_CSV = (
    REPO
    / "data"
    / "identity"
    / "transfermarkt-serie-a-2026-2027.csv"
)

REPORT_FILE = (
    REPO
    / "data"
    / "identity"
    / "transfermarkt-serie-a-2026-2027-report.txt"
)

REQUEST_DELAY_SECONDS = 2.0


HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/151.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "it-IT,it;q=0.9,en;q=0.8",
}


EXPECTED_SERIE_A_CLUBS = {
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


# ============================================================
# UTILITA
# ============================================================

def normalize(text):
    return " ".join((text or "").strip().split())


def slugify(text):
    text = normalize(text).lower()

    replacements = {
        "à": "a",
        "á": "a",
        "â": "a",
        "ä": "a",
        "ã": "a",
        "å": "a",
        "è": "e",
        "é": "e",
        "ê": "e",
        "ë": "e",
        "ì": "i",
        "í": "i",
        "î": "i",
        "ï": "i",
        "ò": "o",
        "ó": "o",
        "ô": "o",
        "ö": "o",
        "õ": "o",
        "ù": "u",
        "ú": "u",
        "û": "u",
        "ü": "u",
        "ç": "c",
    }

    for old, new in replacements.items():
        text = text.replace(old, new)

    text = re.sub(r"[^a-z0-9]+", "-", text)
    return text.strip("-")


def blocked_response(response):
    lower = response.text.lower()

    markers = [
        "verify that you're not a robot",
        "verificare che non sei un robot",
        "javascript is disabled",
        "captcha",
        "access denied",
    ]

    return (
        response.status_code in (403, 429)
        or any(marker in lower for marker in markers)
    )


def fetch(session, url, label):
    print("")
    print(f"[DOWNLOAD] {label}")
    print(url)

    try:
        response = session.get(
            url,
            headers=HEADERS,
            timeout=30,
        )
    except requests.RequestException as exc:
        raise RuntimeError(
            f"Errore rete durante {label}: {exc}"
        ) from exc

    print(
        f"HTTP {response.status_code} - "
        f"{len(response.content)} byte"
    )

    if blocked_response(response):
        raise RuntimeError(
            f"Transfermarkt ha bloccato la richiesta per {label}. "
            "Nessun bypass e' stato tentato."
        )

    if response.status_code != 200:
        raise RuntimeError(
            f"HTTP {response.status_code} durante {label}"
        )

    return response.text


# ============================================================
# SCOPERTA AUTOMATICA CLUB
# ============================================================

def discover_clubs(html):
    soup = BeautifulSoup(html, "lxml")

    candidates = {}

    for link in soup.select('a[href*="/verein/"]'):
        href = link.get("href", "")

        match = re.search(
            r"/(?:startseite|kader)/verein/(\d+)",
            href,
        )

        if not match:
            continue

        club_id = match.group(1)

        name = normalize(
            link.get("title")
            or link.get_text(" ", strip=True)
        )

        if not name:
            continue

        href_lower = href.lower()

        if "saison_id/2026" not in href_lower and "wettbewerb" not in href_lower:
            pass

        existing = candidates.get(club_id)

        if existing is None or len(name) > len(existing["name"]):
            candidates[club_id] = {
                "club_id": club_id,
                "name": name,
                "href": href,
            }

    # Transfermarkt puo' avere diversi link allo stesso club.
    # Qui filtriamo le denominazioni plausibili confrontandole
    # con i 20 club Serie A ufficiali attesi.
    result = []

    aliases = {
        "inter milan": "inter",
        "inter milano": "inter",
        "fc internazionale": "inter",
        "internazionale": "inter",
        "ac milan": "milan",
        "ssc napoli": "napoli",
        "as roma": "roma",
        "ss lazio": "lazio",
        "acf fiorentina": "fiorentina",
        "us lecce": "lecce",
        "torino fc": "torino",
        "udinese calcio": "udinese",
        "venezia fc": "venezia",
        "monza": "monza",
        "ac monza": "monza",
        "parma calcio": "parma",
        "como 1907": "como",
        "genoa cfc": "genoa",
        "bologna fc": "bologna",
        "cagliari calcio": "cagliari",
        "atalanta bc": "atalanta",
        "us sassuolo": "sassuolo",
        "frosinone calcio": "frosinone",
        "juventus fc": "juventus",
    }

    seen_expected = set()

    for item in candidates.values():
        raw = normalize(item["name"]).lower()

        simple = aliases.get(raw)

        if simple is None:
            for expected in EXPECTED_SERIE_A_CLUBS:
                if expected in raw:
                    simple = expected
                    break

        if simple not in EXPECTED_SERIE_A_CLUBS:
            continue

        if simple in seen_expected:
            continue

        seen_expected.add(simple)

        result.append(
            {
                "club_id": item["club_id"],
                "club_key": simple,
                "name": item["name"],
            }
        )

    result.sort(key=lambda x: x["club_key"])

    return result


# ============================================================
# ESTRAZIONE ROSA
# ============================================================

POSITION_TERMS = [
    "Portiere",
    "Difensore centrale",
    "Terzino destro",
    "Terzino sinistro",
    "Mediano",
    "Centrocampista centrale",
    "Centrocampista",
    "Trequartista",
    "Ala destra",
    "Ala sinistra",
    "Seconda punta",
    "Punta centrale",
    "Attaccante",
]


def extract_players(html, club):
    soup = BeautifulSoup(html, "lxml")

    players = {}

    for row in soup.select("table.items > tbody > tr"):
        link = row.select_one(
            'a[href*="/profil/spieler/"]'
        )

        if not link:
            continue

        href = link.get("href", "")

        match = re.search(
            r"/profil/spieler/(\d+)",
            href,
        )

        if not match:
            continue

        player_id = match.group(1)

        name = normalize(
            link.get("title")
            or link.get_text(" ", strip=True)
        )

        if not name:
            continue

        row_text = normalize(
            " ".join(row.stripped_strings)
        )

        # ------------------------
        # Data di nascita
        # ------------------------

        dob = ""

        dob_match = re.search(
            r"\b(\d{2}/\d{2}/\d{4})\b",
            row_text,
        )

        if dob_match:
            try:
                dob = datetime.strptime(
                    dob_match.group(1),
                    "%d/%m/%Y",
                ).strftime("%Y-%m-%d")
            except ValueError:
                pass

        # ------------------------
        # Eta
        # ------------------------

        age = ""

        age_match = re.search(
            r"\(\s*(\d{1,2})\s*\)",
            row_text,
        )

        if age_match:
            age = age_match.group(1)

        # ------------------------
        # Nazionalita
        # ------------------------

        nationalities = []

        for flag in row.select("img.flaggenrahmen"):
            nationality = normalize(
                flag.get("title")
                or flag.get("alt")
            )

            if (
                nationality
                and nationality not in nationalities
            ):
                nationalities.append(nationality)

        # ------------------------
        # Posizione
        # ------------------------

        position = ""

        for term in POSITION_TERMS:
            if term.lower() in row_text.lower():
                position = term
                break

        # ------------------------
        # Altezza
        # ------------------------

        height = ""

        height_match = re.search(
            r"\b(1,\d{2})\s*m\b",
            row_text,
        )

        if height_match:
            height = (
                height_match.group(1)
                + " m"
            )

        players[player_id] = {
            "transfermarkt_player_id": player_id,
            "transfermarkt_club_id": club["club_id"],
            "team": club["club_key"],
            "team_transfermarkt_name": club["name"],
            "full_name": name,
            "date_of_birth": dob,
            "age": age,
            "nationality": "; ".join(nationalities),
            "position": position,
            "height": height,
            "player_url": urljoin(
                BASE_URL,
                href.split("?")[0],
            ),
            "source": "TRANSFERMARKT",
            "season": "2026/2027",
            "observed_at": datetime.now(
                timezone.utc
            ).isoformat(),
        }

    return list(players.values())


# ============================================================
# CSV
# ============================================================

FIELDNAMES = [
    "transfermarkt_player_id",
    "transfermarkt_club_id",
    "team",
    "team_transfermarkt_name",
    "full_name",
    "date_of_birth",
    "age",
    "nationality",
    "position",
    "height",
    "player_url",
    "source",
    "season",
    "observed_at",
]


def write_csv(path, rows):
    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with path.open(
        "w",
        newline="",
        encoding="utf-8-sig",
    ) as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=FIELDNAMES,
        )

        writer.writeheader()
        writer.writerows(rows)


# ============================================================
# MAIN
# ============================================================

def main():
    print(
        "============================================================"
    )
    print(
        " TRANSFERMARKT SERIE A 2026/27 - IDENTITY DATASET"
    )
    print(
        "============================================================"
    )

    print("")
    print(
        "Regola sicurezza: nessun bypass di 403/429/CAPTCHA."
    )

    OUTPUT_DIR.mkdir(
        parents=True,
        exist_ok=True,
    )

    session = requests.Session()

    competition_html = fetch(
        session,
        COMPETITION_URL,
        "Serie A 2026/27",
    )

    clubs = discover_clubs(
        competition_html
    )

    print("")
    print("=== CLUB SCOPERTI ===")

    for club in clubs:
        print(
            f"{club['club_key']:<12} | "
            f"ID {club['club_id']:<6} | "
            f"{club['name']}"
        )

    discovered_keys = {
        c["club_key"]
        for c in clubs
    }

    missing_clubs = sorted(
        EXPECTED_SERIE_A_CLUBS
        - discovered_keys
    )

    unexpected = sorted(
        discovered_keys
        - EXPECTED_SERIE_A_CLUBS
    )

    print("")
    print(
        f"Club scoperti: {len(clubs)}/20"
    )

    if missing_clubs:
        print(
            "MANCANTI: "
            + ", ".join(missing_clubs)
        )

    if unexpected:
        print(
            "NON ATTESI: "
            + ", ".join(unexpected)
        )

    if len(clubs) != 20 or missing_clubs:
        print("")
        print(
            "ERRORE: non sono stati identificati "
            "in modo sicuro tutti i 20 club."
        )
        print(
            "Nessuna rosa verra' scaricata."
        )
        return 10

    all_rows = []
    team_stats = []
    errors = []

    print("")
    print(
        "============================================================"
    )
    print(
        " DOWNLOAD ROSE"
    )
    print(
        "============================================================"
    )

    for index, club in enumerate(
        clubs,
        start=1,
    ):
        squad_url = (
            f"{BASE_URL}/"
            f"{club['club_key']}/kader/"
            f"verein/{club['club_id']}/"
            f"saison_id/{SEASON}/plus/1"
        )

        print("")
        print(
            f"[{index:02d}/20] "
            f"{club['club_key'].upper()}"
        )

        try:
            html = fetch(
                session,
                squad_url,
                club["club_key"],
            )

            players = extract_players(
                html,
                club,
            )

        except RuntimeError as exc:
            print(
                f"ERRORE: {exc}"
            )

            errors.append(
                {
                    "team": club["club_key"],
                    "error": str(exc),
                }
            )

            # Se siamo stati bloccati, fermiamo
            # l'intero processo senza insistere.
            if (
                "bloccato" in str(exc).lower()
                or "429" in str(exc)
                or "403" in str(exc)
            ):
                print("")
                print(
                    "STOP DI SICUREZZA."
                )
                print(
                    "Non verranno effettuate "
                    "altre richieste."
                )
                break

            continue

        if not players:
            errors.append(
                {
                    "team": club["club_key"],
                    "error": (
                        "0 giocatori estratti"
                    ),
                }
            )

            print(
                "ATTENZIONE: 0 giocatori."
            )

            continue

        team_file = (
            OUTPUT_DIR
            / (
                slugify(
                    club["club_key"]
                )
                + ".csv"
            )
        )

        write_csv(
            team_file,
            players,
        )

        all_rows.extend(
            players
        )

        with_dob = sum(
            bool(p["date_of_birth"])
            for p in players
        )

        with_nat = sum(
            bool(p["nationality"])
            for p in players
        )

        with_pos = sum(
            bool(p["position"])
            for p in players
        )

        team_stats.append(
            {
                "team": club["club_key"],
                "players": len(players),
                "dob": with_dob,
                "nationality": with_nat,
                "position": with_pos,
            }
        )

        print(
            f"Giocatori: {len(players)} | "
            f"DOB: {with_dob} | "
            f"Nazionalita: {with_nat} | "
            f"Posizione: {with_pos}"
        )

        if index < len(clubs):
            time.sleep(
                REQUEST_DELAY_SECONDS
            )

    # ========================================================
    # DEDUPLICAZIONE / COLLISIONI
    # ========================================================

    by_player_id = defaultdict(list)

    for row in all_rows:
        by_player_id[
            row["transfermarkt_player_id"]
        ].append(row)

    collisions = []

    unique_rows = []

    for player_id, records in by_player_id.items():
        names = {
            normalize(
                r["full_name"]
            ).lower()
            for r in records
        }

        dobs = {
            r["date_of_birth"]
            for r in records
            if r["date_of_birth"]
        }

        if (
            len(names) > 1
            or len(dobs) > 1
        ):
            collisions.append(
                {
                    "player_id": player_id,
                    "names": sorted(names),
                    "birth_dates": sorted(dobs),
                    "teams": sorted(
                        {
                            r["team"]
                            for r in records
                        }
                    ),
                }
            )

            continue

        # Se lo stesso ID compare in due rose
        # per un trasferimento, conserviamo
        # una sola persona nel master.
        # I club restano verificabili nei CSV
        # per squadra.
        unique_rows.append(
            records[0]
        )

    unique_rows.sort(
        key=lambda x: (
            x["team"],
            x["full_name"].lower(),
        )
    )

    write_csv(
        MASTER_CSV,
        unique_rows,
    )

    # ========================================================
    # REPORT
    # ========================================================

    total = len(unique_rows)

    total_dob = sum(
        bool(r["date_of_birth"])
        for r in unique_rows
    )

    total_nat = sum(
        bool(r["nationality"])
        for r in unique_rows
    )

    total_pos = sum(
        bool(r["position"])
        for r in unique_rows
    )

    report = []

    report.append(
        "TRANSFERMARKT SERIE A 2026/27 - REPORT"
    )

    report.append(
        "=" * 60
    )

    report.append(
        f"Timestamp UTC: "
        f"{datetime.now(timezone.utc).isoformat()}"
    )

    report.append(
        ""
    )

    report.append(
        f"Squadre attese: 20"
    )

    report.append(
        f"Squadre scoperte: {len(clubs)}"
    )

    report.append(
        f"Squadre elaborate: {len(team_stats)}"
    )

    report.append(
        f"Errori squadra: {len(errors)}"
    )

    report.append(
        ""
    )

    report.append(
        f"Righe rosa complessive: {len(all_rows)}"
    )

    report.append(
        f"Giocatori Transfermarkt unici: {total}"
    )

    report.append(
        f"Con data di nascita: {total_dob}"
    )

    report.append(
        f"Con nazionalita: {total_nat}"
    )

    report.append(
        f"Con posizione: {total_pos}"
    )

    report.append(
        f"Collisioni identitarie: {len(collisions)}"
    )

    report.append(
        ""
    )

    report.append(
        "DETTAGLIO SQUADRE"
    )

    report.append(
        "-" * 60
    )

    for stat in sorted(
        team_stats,
        key=lambda x: x["team"],
    ):
        report.append(
            f"{stat['team']:<12} "
            f"players={stat['players']:<3} "
            f"dob={stat['dob']:<3} "
            f"nationality={stat['nationality']:<3} "
            f"position={stat['position']:<3}"
        )

    if errors:
        report.append("")
        report.append(
            "ERRORI"
        )
        report.append(
            "-" * 60
        )

        for item in errors:
            report.append(
                f"{item['team']}: "
                f"{item['error']}"
            )

    if collisions:
        report.append("")
        report.append(
            "COLLISIONI"
        )
        report.append(
            "-" * 60
        )

        for item in collisions:
            report.append(
                str(item)
            )

    REPORT_FILE.write_text(
        "\n".join(report),
        encoding="utf-8",
    )

    # ========================================================
    # RISULTATO TERMINALE
    # ========================================================

    print("")
    print(
        "============================================================"
    )
    print(
        " RISULTATO FINALE"
    )
    print(
        "============================================================"
    )

    print(
        f"Squadre elaborate:        "
        f"{len(team_stats)}/20"
    )

    print(
        f"Righe complessive:        "
        f"{len(all_rows)}"
    )

    print(
        f"Giocatori ID unici:       "
        f"{total}"
    )

    print(
        f"Con data di nascita:      "
        f"{total_dob}"
    )

    print(
        f"Con nazionalita:          "
        f"{total_nat}"
    )

    print(
        f"Con posizione:            "
        f"{total_pos}"
    )

    print(
        f"Collisioni:               "
        f"{len(collisions)}"
    )

    print(
        f"Errori squadra:           "
        f"{len(errors)}"
    )

    print("")
    print(
        f"MASTER CSV:"
    )
    print(
        MASTER_CSV
    )

    print("")
    print(
        f"REPORT:"
    )
    print(
        REPORT_FILE
    )

    if errors:
        return 20

    if collisions:
        return 21

    if len(team_stats) != 20:
        return 22

    print("")
    print(
        "ACQUISIZIONE COMPLETATA SENZA "
        "COLLISIONI IDENTITARIE."
    )

    return 0


if __name__ == "__main__":
    sys.exit(main())