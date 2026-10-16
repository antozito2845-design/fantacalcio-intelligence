import argparse
import csv
import html
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
from pathlib import Path
from urllib.parse import quote, urljoin, urlsplit, urlunsplit

BASE_URL = "https://www.fantacalcio.it"
CLASSIFICA_URL = f"{BASE_URL}/serie-a/classifica"
SEASON = "2026/2027"

ROOT = Path(__file__).resolve().parents[2]

OUTPUT_DIR = (
    ROOT
    / "data"
    / "identity"
    / "fantacalcio-2026-2027"
)

MASTER_FILE = (
    ROOT
    / "data"
    / "identity"
    / "fantacalcio-serie-a-2026-2027.csv"
)

REPORT_FILE = (
    ROOT
    / "data"
    / "identity"
    / "fantacalcio-serie-a-2026-2027-report.txt"
)

USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/151.0.0.0 Safari/537.36"
)

REQUEST_DELAY = 1.0
RETRIES = 3

FIELDS = [
    "fantacalcio_player_id",
    "short_name",
    "full_name",
    "club",
    "fantasy_role",
    "date_of_birth",
    "height",
    "preferred_foot",
    "nationality",
    "player_url",
    "source",
    "season",
]


def clean(value):
    if value is None:
        return ""

    value = re.sub(r"<[^>]+>", "", value)
    value = html.unescape(value)
    return " ".join(value.split()).strip()


def normalize(value):
    value = clean(value).lower()

    value = unicodedata.normalize("NFKD", value)

    value = "".join(
        c for c in value
        if not unicodedata.combining(c)
    )

    value = re.sub(r"[^a-z0-9]+", "-", value)
    return value.strip("-")


def extract(pattern, text, default=""):
    match = re.search(
        pattern,
        text,
        flags=re.I | re.S,
    )

    if not match:
        return default

    return clean(match.group(1))


def download(url):
    last_error = None

    for attempt in range(1, RETRIES + 1):
        try:
            # Gli URL Fantacalcio possono contenere caratteri accentati
            # come Lucumì, Dodò, Soulè, Laurientè.
            # urllib richiede un URL ASCII-safe.
            parts = urlsplit(url)

            safe_url = urlunsplit(
                (
                    parts.scheme,
                    parts.netloc,
                    quote(parts.path, safe="/%"),
                    quote(parts.query, safe="=&%"),
                    parts.fragment,
                )
            )

            request = urllib.request.Request(
                safe_url,
                headers={
                    "User-Agent": USER_AGENT,
                    "Accept": (
                        "text/html,application/xhtml+xml,"
                        "application/xml;q=0.9,image/avif,"
                        "image/webp,*/*;q=0.8"
                    ),
                    "Accept-Language":
                        "it-IT,it;q=0.9,en;q=0.8",
                    "Cache-Control": "no-cache",
                },
            )

            with urllib.request.urlopen(
                request,
                timeout=30,
            ) as response:

                if response.status != 200:
                    raise RuntimeError(
                        f"HTTP {response.status}"
                    )

                raw = response.read()

                # Le pagine Fantacalcio sono UTF-8.
                # Forziamo UTF-8 per evitare mojibake:
                # ì -> Ã¬
                # è -> Ã¨
                # ò -> Ã²
                return raw.decode(
                    "utf-8",
                    errors="replace",
                )

        except Exception as exc:
            last_error = exc

            if attempt < RETRIES:
                wait = attempt * 3

                print(
                    f"      retry {attempt}/{RETRIES} "
                    f"tra {wait}s: {exc}"
                )

                time.sleep(wait)

    raise RuntimeError(
        f"Download fallito dopo {RETRIES} tentativi: "
        f"{url} -> {last_error}"
    )

def discover_teams():
    print("Scopro le squadre dalla classifica Fantacalcio...")

    page = download(CLASSIFICA_URL)

    pattern = re.compile(
        r'href=["\']'
        r'(?P<url>'
        r'(?:https://www\.fantacalcio\.it)?'
        r'/serie-a/squadre/'
        r'(?P<slug>[a-z0-9-]+)'
        r')'
        r'["\']',
        re.I,
    )

    teams = {}

    for match in pattern.finditer(page):
        slug = normalize(match.group("slug"))

        if not slug:
            continue

        teams[slug] = (
            f"{BASE_URL}/serie-a/squadre/{slug}"
        )

    # Fallback sicuro sul campionato 2026/27,
    # nel caso la classifica cambi markup.
    expected = [
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
    ]

    discovered = {
        slug: url
        for slug, url in teams.items()
        if slug in expected
    }

    if set(discovered) != set(expected):
        missing = sorted(
            set(expected) - set(discovered)
        )

        if missing:
            print(
                "ATTENZIONE: classifica non ha esposto "
                f"questi slug: {', '.join(missing)}"
            )

            print(
                "Uso URL squadra deterministici "
                "come fallback."
            )

            for slug in missing:
                discovered[slug] = (
                    f"{BASE_URL}/serie-a/squadre/{slug}"
                )

    return {
        slug: discovered[slug]
        for slug in expected
        if slug in discovered
    }


def discover_players(team_slug, team_url):
    page = download(team_url)

    pattern = re.compile(
        r'href=["\']'
        r'(?P<url>'
        r'(?:https://www\.fantacalcio\.it)?'
        r'/serie-a/squadre/'
        + re.escape(team_slug)
        + r'/[^/"\']+/'
        r'(?P<id>\d+)'
        r')'
        r'["\']',
        re.I,
    )

    players = {}

    for match in pattern.finditer(page):
        player_id = match.group("id")

        url = urljoin(
            BASE_URL,
            html.unescape(match.group("url")),
        )

        players[player_id] = {
            "fantacalcio_player_id": player_id,
            "player_url": url,
        }

    return list(players.values())


def parse_player(player, expected_team_slug):
    page = download(player["player_url"])

    full_name = extract(
        r'<h1[^>]*'
        r'class="[^"]*player-name[^"]*"'
        r'[^>]*itemprop="name"[^>]*>'
        r'(.*?)</h1>',
        page,
    )

    if not full_name:
        full_name = extract(
            r'<title>\s*(.*?)\s*-\s*'
            r'Profilo calciatore',
            page,
        )

    club = extract(
        r'<span[^>]*class="team"[^>]*'
        r'itemprop="memberOf".*?'
        r'<meta[^>]*itemprop="name"'
        r'[^>]*content="([^"]+)"',
        page,
    )

    role = extract(
        r'<span[^>]*'
        r'class="[^"]*\brole\b[^"]*"'
        r'[^>]*data-value="([pdca])"',
        page,
    ).upper()

    birth_date = extract(
        r'<meta[^>]*'
        r'itemprop="birthdate"'
        r'[^>]*content="([^"]+)"',
        page,
    )

    height = extract(
        r'<dd[^>]*itemprop="height"[^>]*>'
        r'(.*?)</dd>',
        page,
    )

    nationality = extract(
        r'<dd[^>]*'
        r'class="[^"]*nationalities[^"]*"'
        r'[^>]*>(.*?)</dd>',
        page,
    )

    preferred_foot = extract(
        r'<dt>\s*Piede\s*</dt>'
        r'\s*<dd>.*?'
        r'<span[^>]*title="([^"]+)"',
        page,
    )

    if not preferred_foot:
        preferred_foot = extract(
            r'<dt>\s*Piede\s*</dt>'
            r'\s*<dd>(.*?)</dd>',
            page,
        )

    canonical_url = extract(
        r'<link[^>]*'
        r'rel="canonical"'
        r'[^>]*href="([^"]+)"',
        page,
    )

    if not canonical_url:
        canonical_url = player["player_url"]

    short_name = extract(
        r'playerName:\s*"([^"]+)"',
        page,
    )

    if not short_name:
        short_name = extract(
            r'<!--\s*'
            r'id:\s*\d+\s*'
            r'name:\s*(.*?)'
            r'\s*-->',
            page,
        )

    if not short_name:
        short_name = full_name

    # ID presente anche nel Bridge JavaScript:
    bridge_id = extract(
        r'playerId:\s*(\d+)',
        page,
    )

    expected_id = str(
        player["fantacalcio_player_id"]
    )

    if bridge_id and bridge_id != expected_id:
        raise RuntimeError(
            f"ID incoerente URL={expected_id} "
            f"Bridge={bridge_id}"
        )

    if not full_name:
        raise RuntimeError(
            "full_name mancante"
        )

    if not birth_date:
        raise RuntimeError(
            "date_of_birth mancante"
        )

    if role not in {"P", "D", "C", "A"}:
        raise RuntimeError(
            f"fantasy_role non valido: {role!r}"
        )

    if not club:
        raise RuntimeError(
            "club mancante"
        )

    if normalize(club) != expected_team_slug:
        raise RuntimeError(
            f"club incoerente: pagina={club}, "
            f"atteso={expected_team_slug}"
        )

    return {
        "fantacalcio_player_id":
            expected_id,
        "short_name":
            short_name,
        "full_name":
            full_name,
        "club":
            club,
        "fantasy_role":
            role,
        "date_of_birth":
            birth_date,
        "height":
            height,
        "preferred_foot":
            preferred_foot,
        "nationality":
            nationality,
        "player_url":
            canonical_url,
        "source":
            "FANTACALCIO",
        "season":
            SEASON,
    }


def write_csv(path, rows):
    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with path.open(
        "w",
        encoding="utf-8-sig",
        newline="",
    ) as handle:

        writer = csv.DictWriter(
            handle,
            fieldnames=FIELDS,
        )

        writer.writeheader()
        writer.writerows(rows)


def read_csv(path):
    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as handle:

        return list(csv.DictReader(handle))


def audit_rows(team_slug, rows):
    ids = [
        row["fantacalcio_player_id"]
        for row in rows
    ]

    return {
        "team": team_slug,
        "rows": len(rows),
        "unique_ids": len(set(ids)),
        "duplicates":
            len(ids) - len(set(ids)),
        "full_name":
            sum(bool(r["full_name"]) for r in rows),
        "dob":
            sum(bool(r["date_of_birth"]) for r in rows),
        "nationality":
            sum(bool(r["nationality"]) for r in rows),
        "height":
            sum(bool(r["height"]) for r in rows),
        "foot":
            sum(bool(r["preferred_foot"]) for r in rows),
        "role":
            sum(
                r["fantasy_role"]
                in {"P", "D", "C", "A"}
                for r in rows
            ),
    }


def acquire_team(team_slug, team_url, force=False):
    output_file = OUTPUT_DIR / f"{team_slug}.csv"

    if output_file.exists() and not force:
        existing = read_csv(output_file)

        # Se il CSV già esiste ed è completo,
        # lo riutilizziamo.
        if existing:
            audit = audit_rows(
                team_slug,
                existing,
            )

            complete = (
                audit["rows"]
                == audit["unique_ids"]
                == audit["full_name"]
                == audit["dob"]
                == audit["role"]
            )

            if complete:
                print(
                    f"[{team_slug}] CSV esistente "
                    f"riutilizzato: {audit['rows']} giocatori"
                )

                return existing, []

    print()
    print("=" * 70)
    print(f"SQUADRA: {team_slug.upper()}")
    print("=" * 70)

    roster = discover_players(
        team_slug,
        team_url,
    )

    print(
        f"Giocatori trovati nella rosa: "
        f"{len(roster)}"
    )

    if not roster:
        return [], [
            (
                team_slug,
                "",
                "NESSUN_GIOCATORE_TROVATO",
            )
        ]

    rows = []
    errors = []

    for index, player in enumerate(
        roster,
        start=1,
    ):
        player_id = (
            player["fantacalcio_player_id"]
        )

        print(
            f"[{index:02d}/{len(roster):02d}] "
            f"ID {player_id}"
        )

        try:
            row = parse_player(
                player,
                team_slug,
            )

            rows.append(row)

            print(
                f"     {row['full_name']} | "
                f"{row['fantasy_role']} | "
                f"{row['date_of_birth']} | "
                f"{row['nationality']}"
            )

        except Exception as exc:
            print(
                f"     ERRORE: {exc}"
            )

            errors.append(
                (
                    team_slug,
                    player_id,
                    str(exc),
                )
            )

        time.sleep(REQUEST_DELAY)

    write_csv(
        output_file,
        rows,
    )

    audit = audit_rows(
        team_slug,
        rows,
    )

    print()
    print(
        f"AUDIT {team_slug.upper()}: "
        f"{audit['rows']} profili | "
        f"DOB {audit['dob']}/{audit['rows']} | "
        f"altezza {audit['height']}/{audit['rows']} | "
        f"piede {audit['foot']}/{audit['rows']} | "
        f"errori {len(errors)}"
    )

    return rows, errors


def build_master(team_slugs):
    all_rows = []

    for team_slug in team_slugs:
        path = OUTPUT_DIR / f"{team_slug}.csv"

        if not path.exists():
            continue

        all_rows.extend(
            read_csv(path)
        )

    all_rows.sort(
        key=lambda r: (
            normalize(r["club"]),
            normalize(r["full_name"]),
            int(r["fantacalcio_player_id"]),
        )
    )

    write_csv(
        MASTER_FILE,
        all_rows,
    )

    return all_rows


def write_report(team_slugs, all_rows, errors):
    audits = []

    for team_slug in team_slugs:
        path = OUTPUT_DIR / f"{team_slug}.csv"

        if not path.exists():
            continue

        rows = read_csv(path)

        audits.append(
            audit_rows(
                team_slug,
                rows,
            )
        )

    ids = [
        r["fantacalcio_player_id"]
        for r in all_rows
    ]

    duplicate_ids = sorted({
        player_id
        for player_id in ids
        if ids.count(player_id) > 1
    })

    lines = []

    lines.append(
        "FANTACALCIO IDENTITY ACQUISITION REPORT"
    )
    lines.append(
        "Season: 2026/2027"
    )
    lines.append("")

    lines.append(
        f"Squadre attese: {len(team_slugs)}"
    )
    lines.append(
        f"Squadre CSV presenti: {len(audits)}"
    )
    lines.append(
        f"Giocatori master: {len(all_rows)}"
    )
    lines.append(
        f"Fantacalcio ID unici: {len(set(ids))}"
    )
    lines.append(
        f"Duplicati globali ID: {len(duplicate_ids)}"
    )
    lines.append(
        f"Full name: "
        f"{sum(bool(r['full_name']) for r in all_rows)}/"
        f"{len(all_rows)}"
    )
    lines.append(
        f"DOB: "
        f"{sum(bool(r['date_of_birth']) for r in all_rows)}/"
        f"{len(all_rows)}"
    )
    lines.append(
        f"Nationality: "
        f"{sum(bool(r['nationality']) for r in all_rows)}/"
        f"{len(all_rows)}"
    )
    lines.append(
        f"Height: "
        f"{sum(bool(r['height']) for r in all_rows)}/"
        f"{len(all_rows)}"
    )
    lines.append(
        f"Preferred foot: "
        f"{sum(bool(r['preferred_foot']) for r in all_rows)}/"
        f"{len(all_rows)}"
    )
    lines.append(
        f"Fantasy role P/D/C/A: "
        f"{sum(r['fantasy_role'] in {'P','D','C','A'} for r in all_rows)}/"
        f"{len(all_rows)}"
    )
    lines.append(
        f"Errori acquisizione: {len(errors)}"
    )

    lines.append("")
    lines.append("PER SQUADRA")
    lines.append("-" * 70)

    for audit in audits:
        lines.append(
            f"{audit['team']}: "
            f"rows={audit['rows']}, "
            f"ids={audit['unique_ids']}, "
            f"dob={audit['dob']}, "
            f"nationality={audit['nationality']}, "
            f"height={audit['height']}, "
            f"foot={audit['foot']}, "
            f"role={audit['role']}, "
            f"duplicates={audit['duplicates']}"
        )

    if duplicate_ids:
        lines.append("")
        lines.append("DUPLICATE PLAYER IDS")

        for player_id in duplicate_ids:
            lines.append(
                f"- {player_id}"
            )

    if errors:
        lines.append("")
        lines.append("ERRORI")

        for team, player_id, error in errors:
            lines.append(
                f"- {team} | {player_id} | {error}"
            )

    REPORT_FILE.write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )


def main():
    parser = argparse.ArgumentParser()

    parser.add_argument(
        "--team",
        help=(
            "Acquisisce una sola squadra, "
            "es. --team atalanta"
        ),
    )

    parser.add_argument(
        "--all",
        action="store_true",
        help="Acquisisce tutte le 20 squadre",
    )

    parser.add_argument(
        "--force",
        action="store_true",
        help="Riscarica anche CSV già completi",
    )

    args = parser.parse_args()

    if not args.team and not args.all:
        parser.error(
            "Usare --team <slug> oppure --all"
        )

    OUTPUT_DIR.mkdir(
        parents=True,
        exist_ok=True,
    )

    teams = discover_teams()

    print(
        f"Squadre disponibili: {len(teams)}"
    )

    print(
        ", ".join(teams.keys())
    )
    print()

    if args.team:
        team_slug = normalize(args.team)

        if team_slug not in teams:
            raise RuntimeError(
                f"Squadra non valida: {team_slug}"
            )

        selected = {
            team_slug: teams[team_slug]
        }

    else:
        selected = teams

    all_errors = []

    for team_slug, team_url in selected.items():

        _, errors = acquire_team(
            team_slug,
            team_url,
            force=args.force,
        )

        all_errors.extend(errors)

    # Il master contiene tutti i CSV squadra
    # presenti nella directory, inclusa Atalanta.
    master_rows = build_master(
        teams.keys()
    )

    write_report(
        teams.keys(),
        master_rows,
        all_errors,
    )

    print()
    print("=" * 70)
    print("AUDIT FINALE FANTACALCIO")
    print("=" * 70)

    ids = [
        r["fantacalcio_player_id"]
        for r in master_rows
    ]

    print(
        f"Squadre attese:           {len(teams)}"
    )

    existing_team_files = sum(
        (OUTPUT_DIR / f"{slug}.csv").exists()
        for slug in teams
    )

    print(
        f"CSV squadre presenti:     {existing_team_files}"
    )

    print(
        f"Giocatori master:         {len(master_rows)}"
    )

    print(
        f"Fantacalcio ID unici:     {len(set(ids))}"
    )

    print(
        f"Duplicati globali ID:     "
        f"{len(ids) - len(set(ids))}"
    )

    print(
        "Full name:                "
        f"{sum(bool(r['full_name']) for r in master_rows)}"
        f"/{len(master_rows)}"
    )

    print(
        "DOB:                      "
        f"{sum(bool(r['date_of_birth']) for r in master_rows)}"
        f"/{len(master_rows)}"
    )

    print(
        "Nationality:              "
        f"{sum(bool(r['nationality']) for r in master_rows)}"
        f"/{len(master_rows)}"
    )

    print(
        "Height:                   "
        f"{sum(bool(r['height']) for r in master_rows)}"
        f"/{len(master_rows)}"
    )

    print(
        "Preferred foot:           "
        f"{sum(bool(r['preferred_foot']) for r in master_rows)}"
        f"/{len(master_rows)}"
    )

    print(
        "Fantasy role P/D/C/A:     "
        f"{sum(r['fantasy_role'] in {'P','D','C','A'} for r in master_rows)}"
        f"/{len(master_rows)}"
    )

    print(
        f"Errori questa esecuzione: {len(all_errors)}"
    )

    print()
    print(
        f"MASTER: {MASTER_FILE}"
    )

    print(
        f"REPORT: {REPORT_FILE}"
    )


if __name__ == "__main__":
    try:
        main()

    except KeyboardInterrupt:
        print()
        print(
            "Interrotto dall'utente. "
            "I CSV delle squadre già completate "
            "restano utilizzabili."
        )
        sys.exit(130)

    except Exception as exc:
        print()
        print(
            f"ERRORE FATALE: {exc}"
        )
        sys.exit(1)
