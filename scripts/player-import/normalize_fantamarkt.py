from __future__ import annotations

import argparse
import csv
import html
import json
import re
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable
from urllib.error import HTTPError, URLError
from urllib.parse import urljoin
from urllib.request import Request, urlopen


BASE_URL = "https://www.transfermarkt.it"
COMPETITION_URL = (
    BASE_URL
    + "/serie-a/startseite/wettbewerb/IT1/saison_id/2026"
)

PROJECT_ROOT = Path(__file__).resolve().parents[2]
OUTPUT_PATH = (
    PROJECT_ROOT
    / "scripts"
    / "player-import"
    / "output"
    / "players-fantamarkt-2026-2027.csv"
)
REPORT_PATH = (
    PROJECT_ROOT
    / "scripts"
    / "player-import"
    / "logs"
    / "fantamarkt-normalization-report.json"
)

RAW_HTML_DIR = (
    PROJECT_ROOT
    / "scripts"
    / "player-import"
    / "logs"
    / "fantamarkt-raw"
)

EXPECTED_CLUBS = {
    "Atalanta",
    "Bologna",
    "Cagliari",
    "Como",
    "Fiorentina",
    "Frosinone",
    "Genoa",
    "Inter",
    "Juventus",
    "Lazio",
    "Lecce",
    "Milan",
    "Monza",
    "Napoli",
    "Parma",
    "Roma",
    "Sassuolo",
    "Torino",
    "Udinese",
    "Venezia",
}

CLUB_ALIASES = {
    "Atalanta BC": "Atalanta",
    "Atalanta": "Atalanta",
    "Bologna FC 1909": "Bologna",
    "Bologna": "Bologna",
    "Cagliari Calcio": "Cagliari",
    "Cagliari": "Cagliari",
    "Como 1907": "Como",
    "Como": "Como",
    "ACF Fiorentina": "Fiorentina",
    "Fiorentina": "Fiorentina",
    "Frosinone Calcio": "Frosinone",
    "Frosinone": "Frosinone",
    "Genoa CFC": "Genoa",
    "Genoa": "Genoa",
    "Inter Milan": "Inter",
    "Inter": "Inter",
    "FC Internazionale": "Inter",
    "Juventus FC": "Juventus",
    "Juventus": "Juventus",
    "SS Lazio": "Lazio",
    "Lazio": "Lazio",
    "US Lecce": "Lecce",
    "Lecce": "Lecce",
    "AC Milan": "Milan",
    "Milan": "Milan",
    "AC Monza": "Monza",
    "Monza": "Monza",
    "SSC Napoli": "Napoli",
    "Napoli": "Napoli",
    "Parma Calcio 1913": "Parma",
    "Parma": "Parma",
    "AS Roma": "Roma",
    "Roma": "Roma",
    "US Sassuolo": "Sassuolo",
    "Sassuolo": "Sassuolo",
    "Torino FC": "Torino",
    "Torino": "Torino",
    "Udinese Calcio": "Udinese",
    "Udinese": "Udinese",
    "Venezia FC": "Venezia",
    "Venezia": "Venezia",
}

POSITION_TO_ROLE = {
    "goalkeeper": "P",
    "portiere": "P",
    "centre-back": "D",
    "center-back": "D",
    "difensore centrale": "D",
    "left-back": "D",
    "right-back": "D",
    "terzino sinistro": "D",
    "terzino destro": "D",
    "defender": "D",
    "difensore": "D",
    "defensive midfield": "C",
    "mediano": "C",
    "central midfield": "C",
    "centrocampista centrale": "C",
    "left midfield": "C",
    "right midfield": "C",
    "esterno di sinistra": "C",
    "esterno di destra": "C",
    "attacking midfield": "C",
    "trequartista": "C",
    "midfield": "C",
    "centrocampista": "C",
    "left winger": "A",
    "right winger": "A",
    "ala sinistra": "A",
    "ala destra": "A",
    "second striker": "A",
    "seconda punta": "A",
    "centre-forward": "A",
    "center-forward": "A",
    "punta centrale": "A",
    "forward": "A",
    "attaccante": "A",
    "attacco": "A",
}

PLAYER_LINK_RE = re.compile(
    r'href=["\'](?P<href>[^"\']*/profil/spieler/(?P<id>\d+)[^"\']*)["\']',
    re.IGNORECASE,
)
TEAM_LINK_RE = re.compile(
    r'href=["\'](?P<href>[^"\']*/(?:kader|startseite)/verein/(?P<id>\d+)/saison_id/2026[^"\']*)["\']',
    re.IGNORECASE,
)
TAG_RE = re.compile(r"<[^>]+>")
SPACE_RE = re.compile(r"\s+")

CHALLENGE_SIGNALS = (
    "captcha",
    "cf-chl-",
    "access denied",
    "verify you are human",
    "verifica di essere umano",
    "javascript is disabled",
    "enable javascript",
    "abilita javascript",
)


@dataclass(frozen=True)
class TeamRef:
    club: str
    team_id: str
    squad_url: str


@dataclass(frozen=True)
class PlayerRow:
    external_id: str
    name: str
    club: str
    role: str


@dataclass(frozen=True)
class RejectedPlayer:
    external_id: str
    name: str
    club: str
    reason: str
    context: str


class SourceUnavailable(RuntimeError):
    pass


def clean_text(value: str) -> str:
    value = html.unescape(value)
    value = TAG_RE.sub(" ", value)
    return SPACE_RE.sub(" ", value).strip()


def page_is_challenge(document: str) -> bool:
    lowered = document.casefold()
    return any(signal in lowered for signal in CHALLENGE_SIGNALS)


def page_has_expected_content(
    document: str,
    content_kind: str,
) -> bool:
    if content_kind == "competition":
        return len(TEAM_LINK_RE.findall(document)) >= 15

    if content_kind == "squad":
        return len(PLAYER_LINK_RE.findall(document)) >= 15

    raise ValueError(
        f"Tipo contenuto non supportato: {content_kind}"
    )


def page_matches_expected_team(
    document: str,
    expected_team_id: str | None,
) -> bool:
    if not expected_team_id:
        return True

    decoded = html.unescape(document)

    team_pattern = re.compile(
    rf"/verein/{re.escape(str(expected_team_id))}(?!\d)",
    re.IGNORECASE,
)

    return bool(team_pattern.search(decoded))


def http_get(url: str, timeout: int = 30) -> str:
    request = Request(
        url,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/151.0.0.0 Safari/537.36"
            ),
            "Accept-Language": "it-IT,it;q=0.9,en;q=0.8",
            "Accept": "text/html,application/xhtml+xml",
        },
    )

    try:
        with urlopen(request, timeout=timeout) as response:
            status = getattr(response, "status", 200)
            if status != 200:
                raise SourceUnavailable(
                    f"HTTP {status} da {url}"
                )
            data = response.read()
    except HTTPError as exc:
        raise SourceUnavailable(
            f"HTTP {exc.code} da {url}"
        ) from exc
    except URLError as exc:
        raise SourceUnavailable(
            f"Errore rete verso {url}: {exc.reason}"
        ) from exc

    text = data.decode("utf-8", errors="replace")

    if len(text) < 20_000:
        raise SourceUnavailable(
            f"HTML insolitamente corto ({len(text)} byte) da {url}"
        )

    lowered = text.casefold()
    blocking_signals = (
        "service temporarily unavailable",
        "access denied",
        "captcha",
        "cf-chl-",
        "javascript is disabled",
        "enable javascript",
    )

    if any(signal in lowered for signal in blocking_signals):
        raise SourceUnavailable(
            f"Pagina di blocco/errore ricevuta da {url}"
        )

    return text


def create_visible_browser(browser_name: str):
    try:
        from selenium import webdriver
    except ImportError as exc:
        raise SourceUnavailable(
            "Selenium non installato. Nel TEST SERVER esegui: "
            "python -m pip install selenium"
        ) from exc

    normalized = browser_name.strip().casefold()

    try:
        if normalized in {"auto", "chrome"}:
            try:
                options = webdriver.ChromeOptions()
                options.add_argument("--start-maximized")
                return webdriver.Chrome(options=options)
            except Exception:
                if normalized == "chrome":
                    raise

        if normalized in {"auto", "edge"}:
            options = webdriver.EdgeOptions()
            options.add_argument("--start-maximized")
            return webdriver.Edge(options=options)

    except Exception as exc:
        raise SourceUnavailable(
            "Impossibile avviare Chrome/Edge tramite Selenium: "
            f"{type(exc).__name__}: {exc}"
        ) from exc

    raise SourceUnavailable(
        f"Browser non supportato: {browser_name}"
    )


def browser_get(
    driver,
    url: str,
    *,
    content_kind: str,
    timeout_seconds: int,
    poll_seconds: float = 1.0,
    expected_team_id: str | None = None,
) -> str:
    driver.get(url)

    deadline = time.monotonic() + max(1, timeout_seconds)
    challenge_announced = False
    last_document = ""

    while time.monotonic() < deadline:
        try:
            document = driver.page_source or ""
        except Exception as exc:
            raise SourceUnavailable(
                "Impossibile leggere la pagina dal browser: "
                f"{type(exc).__name__}: {exc}"
            ) from exc

        last_document = document

        content_is_valid = page_has_expected_content(
            document,
            content_kind,
        )

        team_is_valid = page_matches_expected_team(
            document,
            expected_team_id,
        )

        if content_is_valid and team_is_valid:
            return document

        if (
            page_is_challenge(document)
            and not challenge_announced
        ):
            print("")
            print(
                "Transfermarkt richiede una verifica nel browser."
            )
            print(
                "Completa manualmente l'eventuale verifica "
                "nella finestra aperta."
            )
            print(
                "Lo script NON aggira la verifica: attende "
                "che la pagina normale sia disponibile."
            )
            print("")
            challenge_announced = True

        time.sleep(max(0.1, poll_seconds))

    kind_label = (
        "pagina campionato"
        if content_kind == "competition"
        else "pagina rosa"
    )

    challenge_suffix = (
        " La pagina risulta ancora una challenge/verifica."
        if page_is_challenge(last_document)
        else ""
    )

    raise SourceUnavailable(
        f"Timeout in attesa di contenuto affidabile per {kind_label}: "
        f"{url}.{challenge_suffix}"
    )


def safe_file_name(value: str) -> str:
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "-", value.strip())
    return cleaned.strip("-") or "unknown"


def write_raw_html(
    directory: Path,
    file_name: str,
    document: str,
) -> Path:
    directory.mkdir(
        parents=True,
        exist_ok=True,
    )
    path = directory / safe_file_name(file_name)
    temporary = path.with_suffix(
        path.suffix + ".tmp"
    )
    temporary.write_text(
        document,
        encoding="utf-8",
    )
    temporary.replace(path)
    return path


def canonical_club(name: str) -> str | None:
    cleaned = clean_text(name)
    direct = CLUB_ALIASES.get(cleaned)
    if direct:
        return direct

    folded = cleaned.casefold()
    for alias, canonical in CLUB_ALIASES.items():
        if alias.casefold() == folded:
            return canonical
    return None


def enclosing_row_html(
    document: str,
    start: int,
    end: int,
) -> str:
    # Transfermarkt nests a second table inside each outer squad row.
    # The player link is inside the first inner <tr>, while the
    # position is inside the second inner <tr>. Therefore the nearest
    # <tr> is too small: prefer the outer squad row (odd/even).
    prefix = document[:start]

    outer_candidates = [
        prefix.rfind('<tr class="odd"'),
        prefix.rfind('<tr class="even"'),
        prefix.rfind("<tr class='odd'"),
        prefix.rfind("<tr class='even'"),
    ]
    row_start = max(outer_candidates)

    if row_start >= 0:
        next_outer_positions = [
            position
            for position in (
                document.find('<tr class="odd"', end),
                document.find('<tr class="even"', end),
                document.find("<tr class='odd'", end),
                document.find("<tr class='even'", end),
            )
            if position >= 0
        ]

        row_end = (
            min(next_outer_positions)
            if next_outer_positions
            else document.find("</tbody>", end)
        )

        if row_end >= 0:
            return document[row_start:row_end]

    # Generic fallback for synthetic/test markup.
    row_start = document.rfind("<tr", 0, start)
    row_end = document.find("</tr>", end)

    if row_start >= 0 and row_end >= 0:
        return document[row_start:row_end + 5]

    return document[
        max(0, start - 1500):
        min(len(document), end + 2500)
    ]


def discover_teams(document: str) -> list[TeamRef]:
    found: dict[str, TeamRef] = {}

    for match in TEAM_LINK_RE.finditer(document):
        context = clean_text(
            enclosing_row_html(
                document,
                match.start(),
                match.end(),
            )
        )

        club = None
        for alias in sorted(
            CLUB_ALIASES,
            key=len,
            reverse=True,
        ):
            if alias.casefold() in context.casefold():
                club = CLUB_ALIASES[alias]
                break

        if club is None:
            continue

        team_id = match.group("id")
        href = html.unescape(match.group("href"))
        normalized_href = re.sub(
            r"/(?:startseite|kader)/verein/",
            "/kader/verein/",
            href.split("?")[0],
            count=1,
        )
        squad_url = urljoin(
            BASE_URL,
            normalized_href,
        )

        if "/saison_id/2026" not in squad_url:
            squad_url = (
                squad_url.rstrip("/")
                + "/saison_id/2026"
            )

        found.setdefault(
            club,
            TeamRef(
                club=club,
                team_id=team_id,
                squad_url=squad_url,
            ),
        )

    missing = sorted(EXPECTED_CLUBS - set(found))
    if missing:
        raise RuntimeError(
            "Impossibile individuare tutte le 20 squadre. "
            "Mancano: "
            + ", ".join(missing)
        )

    return [
        found[club]
        for club in sorted(found)
    ]


def infer_position(context: str) -> str | None:
    folded = context.casefold()
    candidates = sorted(
        POSITION_TO_ROLE,
        key=len,
        reverse=True,
    )

    for position in candidates:
        if position in folded:
            return position
    return None


def extract_anchor_name(
    document: str,
    match: re.Match[str],
) -> str | None:
    anchor_start = document.rfind(
        "<a",
        0,
        match.start(),
    )
    if anchor_start < 0:
        return None

    anchor_end = document.find(
        "</a>",
        match.end(),
    )
    if anchor_end < 0:
        return None

    anchor_html = document[
        anchor_start:anchor_end + 4
    ]
    name = clean_text(anchor_html)

    if not name or len(name) > 120:
        return None

    return name


def parse_squad_page_with_diagnostics(
    document: str,
    club: str,
) -> tuple[list[PlayerRow], list[RejectedPlayer]]:
    by_id: dict[str, PlayerRow] = {}
    rejected_by_id: dict[str, RejectedPlayer] = {}

    for match in PLAYER_LINK_RE.finditer(document):
        player_id = match.group("id")
        external_id = f"TRANSFERMARKT-{player_id}"

        name = extract_anchor_name(
            document,
            match,
        )

        row_html = enclosing_row_html(
            document,
            match.start(),
            match.end(),
        )
        row_text = clean_text(row_html)

        if not name:
            rejected_by_id.setdefault(
                player_id,
                RejectedPlayer(
                    external_id=external_id,
                    name="",
                    club=club,
                    reason="NAME_NOT_EXTRACTED",
                    context=row_text[:1000],
                ),
            )
            continue

        position = infer_position(row_text)

        if position is None:
            rejected_by_id.setdefault(
                player_id,
                RejectedPlayer(
                    external_id=external_id,
                    name=name,
                    club=club,
                    reason="POSITION_NOT_MAPPED",
                    context=row_text[:1000],
                ),
            )
            continue

        role = POSITION_TO_ROLE[position]

        existing = by_id.get(player_id)

        row = PlayerRow(
            external_id=external_id,
            name=name,
            club=club,
            role=role,
        )

        if existing is None:
            by_id[player_id] = row
            rejected_by_id.pop(player_id, None)
            continue

        if len(row.name) > len(existing.name):
            by_id[player_id] = row

    rows = sorted(
        by_id.values(),
        key=lambda row: (
            row.role,
            row.name.casefold(),
            row.external_id,
        ),
    )

    rejected = sorted(
        rejected_by_id.values(),
        key=lambda item: (
            item.reason,
            item.name.casefold(),
            item.external_id,
        ),
    )

    if len(rows) < 15:
        raise RuntimeError(
            f"Rosa {club}: estratti solo {len(rows)} giocatori. "
            "Parser non considerato affidabile."
        )

    return rows, rejected



def parse_squad_page(
    document: str,
    club: str,
) -> list[PlayerRow]:
    rows, _ = parse_squad_page_with_diagnostics(
        document,
        club,
    )
    return rows


def quarantine_cross_club_duplicates(
    rows: list[PlayerRow],
) -> tuple[list[PlayerRow], list[dict[str, object]]]:
    occurrences: dict[
        str,
        list[PlayerRow],
    ] = {}

    for row in rows:
        occurrences.setdefault(
            row.external_id,
            [],
        ).append(row)

    conflicted_ids = {
        external_id
        for external_id, items in occurrences.items()
        if len({item.club for item in items}) > 1
    }

    conflicts: list[dict[str, object]] = []

    for external_id in sorted(conflicted_ids):
        items = occurrences[external_id]

        conflicts.append(
            {
                "external_id": external_id,
                "names": sorted(
                    {
                        item.name
                        for item in items
                    }
                ),
                "clubs": sorted(
                    {
                        item.club
                        for item in items
                    }
                ),
                "action": "QUARANTINED",
            }
        )

    filtered = [
        row
        for row in rows
        if row.external_id not in conflicted_ids
    ]

    return filtered, conflicts


def validate_all_players(
    rows: list[PlayerRow],
) -> None:
    if not rows:
        raise RuntimeError(
            "Nessun giocatore estratto"
        )

    external_ids: set[str] = set()
    clubs: set[str] = set()
    invalid_roles: list[str] = []

    for row in rows:
        if row.external_id in external_ids:
            raise RuntimeError(
                "External ID duplicato: "
                f"{row.external_id}"
            )

        external_ids.add(row.external_id)
        clubs.add(row.club)

        if row.role not in {"P", "D", "C", "A"}:
            invalid_roles.append(
                f"{row.name}={row.role}"
            )

    if invalid_roles:
        raise RuntimeError(
            "Ruoli non validi: "
            + ", ".join(invalid_roles[:10])
        )

    missing_clubs = sorted(
        EXPECTED_CLUBS - clubs
    )
    if missing_clubs:
        raise RuntimeError(
            "Squadre senza giocatori: "
            + ", ".join(missing_clubs)
        )

    if len(rows) < 400:
        raise RuntimeError(
            f"Estratti solo {len(rows)} giocatori: "
            "soglia minima di sicurezza non raggiunta"
        )


def write_csv_atomic(
    rows: list[PlayerRow],
    output_path: Path,
) -> None:
    output_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    temporary = output_path.with_suffix(
        output_path.suffix + ".tmp"
    )

    with temporary.open(
        "w",
        encoding="utf-8",
        newline="",
    ) as handle:
        writer = csv.DictWriter(
            handle,
            fieldnames=(
                "external_id",
                "name",
                "club",
                "role",
                "quotation",
                "fmv",
            ),
        )
        writer.writeheader()

        for row in rows:
            writer.writerow(
                {
                    "external_id": row.external_id,
                    "name": row.name,
                    "club": row.club,
                    "role": row.role,
                    "quotation": "",
                    "fmv": "",
                }
            )

    temporary.replace(output_path)


def write_report(
    report_path: Path,
    *,
    status: str,
    rows: list[PlayerRow],
    teams: list[TeamRef],
    fetch_mode: str,
    duplicate_conflicts: list[dict[str, object]] | None = None,
    rejected_players: list[RejectedPlayer] | None = None,
    raw_html_files: list[str] | None = None,
    error: str | None = None,
) -> None:
    report_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    role_counts = {
        role: 0
        for role in ("P", "D", "C", "A")
    }
    club_counts = {
        club: 0
        for club in sorted(EXPECTED_CLUBS)
    }

    for row in rows:
        role_counts[row.role] += 1
        club_counts[row.club] += 1

    payload = {
        "source": "FANTAMARKT",
        "season": "2026/2027",
        "status": status,
        "observed_at": (
            datetime.now(timezone.utc).isoformat()
        ),
        "competition_url": COMPETITION_URL,
        "fetch_mode": fetch_mode,
        "output": str(OUTPUT_PATH),
        "total_players": len(rows),
        "role_counts": role_counts,
        "club_counts": club_counts,
        "teams": [
            asdict(team)
            for team in teams
        ],
        "duplicate_conflicts": (
            duplicate_conflicts or []
        ),
        "quarantined_duplicate_players": len(
            duplicate_conflicts or []
        ),
        "rejected_players": [
            asdict(item)
            for item in (rejected_players or [])
        ],
        "rejected_players_count": len(
            rejected_players or []
        ),
        "raw_html_files": raw_html_files or [],
        "error": error,
    }

    report_path.write_text(
        json.dumps(
            payload,
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )


def run(
    *,
    output_path: Path,
    report_path: Path,
    delay_seconds: float,
    fetch_mode: str = "browser",
    browser_name: str = "auto",
    browser_timeout_seconds: int = 180,
) -> int:
    rows: list[PlayerRow] = []
    teams: list[TeamRef] = []
    duplicate_conflicts: list[dict[str, object]] = []
    rejected_players: list[RejectedPlayer] = []
    raw_html_files: list[str] = []
    driver = None

    try:
        if fetch_mode == "browser":
            driver = create_visible_browser(
                browser_name
            )

            competition_html = browser_get(
                driver,
                COMPETITION_URL,
                content_kind="competition",
                timeout_seconds=browser_timeout_seconds,
            )
        elif fetch_mode == "http":
            competition_html = http_get(
                COMPETITION_URL
            )
        else:
            raise ValueError(
                f"Fetch mode non supportato: {fetch_mode}"
            )

        raw_html_files.append(
            str(
                write_raw_html(
                    RAW_HTML_DIR,
                    "00-competition.html",
                    competition_html,
                )
            )
        )

        teams = discover_teams(
            competition_html
        )

        for index, team in enumerate(
            teams,
            start=1,
        ):
            print(
                f"[{index:02d}/{len(teams):02d}] "
                f"{team.club}: {team.squad_url}"
            )

            if fetch_mode == "browser":
                squad_html = browser_get(
                    driver,
                    team.squad_url,
                    content_kind="squad",
                    timeout_seconds=browser_timeout_seconds,
                    expected_team_id=team.team_id,
                )
            else:
                squad_html = http_get(
                    team.squad_url
                )

            raw_html_files.append(
                str(
                    write_raw_html(
                        RAW_HTML_DIR,
                        f"{index:02d}-{team.club}.html",
                        squad_html,
                    )
                )
            )

            squad_rows, squad_rejected = parse_squad_page_with_diagnostics(
                squad_html,
                team.club,
            )

            print(
                "    giocatori estratti: "
                f"{len(squad_rows)}"
            )

            if squad_rejected:
                print(
                    "    giocatori non importati: "
                    f"{len(squad_rejected)}"
                )
                for item in squad_rejected:
                    print(
                        "      "
                        + item.external_id
                        + " | "
                        + (item.name or "<nome non estratto>")
                        + " | "
                        + item.reason
                    )

            rows.extend(squad_rows)
            rejected_players.extend(squad_rejected)

            if (
                delay_seconds > 0
                and index < len(teams)
            ):
                time.sleep(delay_seconds)

        rows, duplicate_conflicts = (
            quarantine_cross_club_duplicates(
                rows
            )
        )

        if duplicate_conflicts:
            print("")
            print(
                "DUPLICATI CROSS-CLUB IN QUARANTENA"
            )
            for conflict in duplicate_conflicts:
                print(
                    "  "
                    + str(conflict["external_id"])
                    + " -> "
                    + ", ".join(
                        conflict["clubs"]
                    )
                )
            print(
                "Giocatori esclusi dal batch: "
                f"{len(duplicate_conflicts)}"
            )
            print("")

        if rejected_players:
            raise RuntimeError(
                "Acquisizione incompleta: "
                f"{len(rejected_players)} giocatori trovati nelle pagine "
                "non sono stati normalizzati. "
                "Controllare rejected_players e gli HTML grezzi nel report."
            )

        validate_all_players(rows)

        write_csv_atomic(
            rows,
            output_path,
        )
        write_report(
            report_path,
            status="COMPLETED",
            rows=rows,
            teams=teams,
            fetch_mode=fetch_mode,
            duplicate_conflicts=duplicate_conflicts,
            rejected_players=rejected_players,
            raw_html_files=raw_html_files,
        )

        print("")
        print(
            "FANTAMARKT NORMALIZZAZIONE COMPLETATA"
        )
        print(f"Giocatori: {len(rows)}")
        print(f"CSV: {output_path}")
        print(f"Report: {report_path}")
        return 0

    except Exception as exc:
        message = (
            f"{type(exc).__name__}: {exc}"
        )

        write_report(
            report_path,
            status="FAILED",
            rows=rows,
            teams=teams,
            fetch_mode=fetch_mode,
            duplicate_conflicts=duplicate_conflicts,
            rejected_players=rejected_players,
            raw_html_files=raw_html_files,
            error=message,
        )

        print("")
        print(
            "FANTAMARKT NORMALIZZAZIONE FALLITA"
        )
        print(message)
        print(
            "Il CSV definitivo NON e stato "
            "sovrascritto."
        )
        return 1

    finally:
        if driver is not None:
            try:
                driver.quit()
            except Exception:
                pass


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Acquisisce e normalizza le rose Serie A "
            "2026/2027 da Transfermarkt in formato "
            "FANTAMARKT."
        )
    )

    parser.add_argument(
        "--output",
        type=Path,
        default=OUTPUT_PATH,
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=REPORT_PATH,
    )
    parser.add_argument(
        "--delay",
        type=float,
        default=1.5,
        help=(
            "Pausa tra richieste squadra "
            "(default 1.5 secondi)"
        ),
    )
    parser.add_argument(
        "--fetch-mode",
        choices=("browser", "http"),
        default="browser",
        help=(
            "browser = Selenium visibile; "
            "http = urllib legacy"
        ),
    )
    parser.add_argument(
        "--browser",
        choices=("auto", "chrome", "edge"),
        default="auto",
        help=(
            "Browser Selenium da usare "
            "(default auto)"
        ),
    )
    parser.add_argument(
        "--browser-timeout",
        type=int,
        default=180,
        help=(
            "Secondi massimi per pagina, inclusa "
            "eventuale verifica manuale "
            "(default 180)"
        ),
    )

    return parser.parse_args()


def main() -> int:
    args = parse_arguments()

    return run(
        output_path=args.output,
        report_path=args.report,
        delay_seconds=max(
            0.0,
            args.delay,
        ),
        fetch_mode=args.fetch_mode,
        browser_name=args.browser,
        browser_timeout_seconds=max(
            30,
            args.browser_timeout,
        ),
    )


if __name__ == "__main__":
    sys.exit(main())







