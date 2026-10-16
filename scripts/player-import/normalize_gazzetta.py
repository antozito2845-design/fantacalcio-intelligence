from __future__ import annotations

import csv
import re
import sys
from dataclasses import dataclass
from pathlib import Path

from pypdf import PdfReader


PROJECT_ROOT = Path(__file__).resolve().parents[2]

PDF_PATH = (
    PROJECT_ROOT
    / "scripts"
    / "player-import"
    / "input"
    / "gazzetta"
    / "listone-gazzetta-2026-2027.pdf"
)

OUTPUT_PATH = (
    PROJECT_ROOT
    / "scripts"
    / "player-import"
    / "output"
    / "players-gazzetta-2026-2027.csv"
)

REPORT_PATH = (
    PROJECT_ROOT
    / "scripts"
    / "player-import"
    / "logs"
    / "gazzetta-normalization-report.txt"
)

CLUB_ALIASES = {
    "Atalanta": "Atalanta",
    "Bologna": "Bologna",
    "Cagliari": "Cagliari",
    "Como": "Como",
    "Fiorentina": "Fiorentina",
    "Frosinone": "Frosinone",
    "Genoa": "Genoa",
    "Inter": "Inter",
    "Internazionale": "Inter",
    "Juventus": "Juventus",
    "Lazio": "Lazio",
    "Lecce": "Lecce",
    "Milan": "Milan",
    "Monza": "Monza",
    "Napoli": "Napoli",
    "Parma": "Parma",
    "Roma": "Roma",
    "Sassuolo": "Sassuolo",
    "Torino": "Torino",
    "Udinese": "Udinese",
    "Venezia": "Venezia",
}

CLUBS = set(CLUB_ALIASES.values())

ROLE_MARKERS = {
    "Portieri": "P",
    "Difensori": "D",
    "Centrocampisti": "C",
    "Attaccanti": "A",
}

IGNORED_LINES = {
    "IL LISTONE",
    "Nome Squadra Costo (Fantamilioni)",
    "fantacampionato.gazzetta.it",
}

INTEGER_PATTERN = re.compile(r"^\d+$")


@dataclass(frozen=True)
class PlayerRow:
    external_id: str
    name: str
    club: str
    role: str
    quotation: int


def clean_line(value: str) -> str:
    cleaned = " ".join(value.strip().split())

    compact = re.sub(
        r"[^a-z0-9]",
        "",
        cleaned.casefold(),
    )

    header_words = (
        "nome",
        "squadra",
        "costo",
        "fantamilioni",
    )

    if all(
        word in compact
        for word in header_words
    ):
        return ""

    if "fantacampionatogazzettait" in compact:
        return ""

    return cleaned


def is_cost(value: str) -> bool:
    return bool(INTEGER_PATTERN.fullmatch(value))


def is_club(value: str) -> bool:
    return value in CLUB_ALIASES


def normalize_club(value: str) -> str:
    try:
        return CLUB_ALIASES[value]
    except KeyError as exception:
        raise RuntimeError(
            f"Squadra non riconosciuta: {value}"
        ) from exception


def split_compound_player_row(value: str) -> list[str]:
    """
    Gestisce righe PDF fuse nel formato:
    NOME GIOCATORE SQUADRA COSTO

    Esempio:
    ZIELINSKI Inter 39
    """

    club_names = sorted(
        CLUB_ALIASES.keys(),
        key=len,
        reverse=True,
    )

    clubs_pattern = "|".join(
        re.escape(club)
        for club in club_names
    )

    pattern = re.compile(
        rf"^(.+?)\s+({clubs_pattern})\s+(\d+)$",
        re.IGNORECASE,
    )

    match = pattern.fullmatch(value)

    if not match:
        return [value]

    player_name = clean_line(match.group(1))
    extracted_club = match.group(2)
    cost = match.group(3)

    normalized_source_club = next(
        club
        for club in club_names
        if club.casefold() == extracted_club.casefold()
    )

    return [
        player_name,
        normalized_source_club,
        cost,
    ]


GAZZETTA_NAME_OVERRIDES = {
    "GAZZETTA-2026-P-0028": "Martinez Jo.",
    "GAZZETTA-2026-A-0080": "Martinez L.",
    "GAZZETTA-2026-P-0060": "Terracciano",
    "GAZZETTA-2026-D-0172": "Terracciano F.",
    "GAZZETTA-2026-D-0071": "Gelli J.",
    "GAZZETTA-2026-C-0052": "Gelli F.",
    "GAZZETTA-2026-D-0139": "Oyono A.",
    "GAZZETTA-2026-D-0140": "Oyono J.",
}


def normalize_player_name(
    external_id: str,
    source_name: str,
) -> str:
    return GAZZETTA_NAME_OVERRIDES.get(
        external_id,
        source_name,
    )

def extract_tokens(reader: PdfReader) -> list[str]:
    tokens: list[str] = []

    for page_number, page in enumerate(reader.pages, start=1):
        text = page.extract_text() or ""

        if not text.strip():
            raise RuntimeError(
                f"La pagina {page_number} non contiene testo estraibile"
            )

        for raw_line in text.splitlines():
            line = clean_line(raw_line)

            if not line:
                continue

            if line in IGNORED_LINES:
                continue

            tokens.extend(
                split_compound_player_row(line)
            )

    return tokens


def next_data_index(
    tokens: list[str],
    start: int,
) -> int:
    index = start

    while index < len(tokens):
        token = tokens[index]

        if token in IGNORED_LINES:
            index += 1
            continue

        return index

    return index


def parse_players(tokens: list[str]) -> tuple[list[PlayerRow], list[str]]:
    players: list[PlayerRow] = []
    diagnostics: list[str] = []

    current_role = "P"
    index = 0
    role_counters = {
        "P": 0,
        "D": 0,
        "C": 0,
        "A": 0,
    }

    while index < len(tokens):
        index = next_data_index(tokens, index)

        if index >= len(tokens):
            break

        token = tokens[index]

        if token == "Allenatori":
            diagnostics.append(
                "Sezione Allenatori rilevata: parsing giocatori terminato"
            )
            break

        if token in ROLE_MARKERS:
            current_role = ROLE_MARKERS[token]
            diagnostics.append(
                f"Sezione ruolo rilevata: {token} -> {current_role}"
            )
            index += 1
            continue

        names: list[str] = []

        while index < len(tokens):
            token = tokens[index]

            if token == "Allenatori":
                diagnostics.append(
                    "Sezione Allenatori rilevata durante un blocco"
                )
                index = len(tokens)
                break

            if token in ROLE_MARKERS:
                current_role = ROLE_MARKERS[token]
                diagnostics.append(
                    f"Sezione ruolo rilevata: "
                    f"{token} -> {current_role}"
                )
                index += 1
                continue

            if is_club(token):
                break

            if is_cost(token):
                raise RuntimeError(
                    "Trovato un costo prima dell'elenco squadre: "
                    f"{token} alla posizione {index}"
                )

            names.append(token)
            index += 1

        if not names:
            raise RuntimeError(
                "Impossibile individuare un blocco di nomi "
                f"alla posizione {index}: {tokens[index:index + 10]}"
            )

        clubs: list[str] = []

        while index < len(tokens) and is_club(tokens[index]):
            clubs.append(
                normalize_club(tokens[index])
            )
            index += 1

        if len(clubs) != len(names):
            raise RuntimeError(
                "Numero squadre diverso dal numero giocatori. "
                f"Ruolo={current_role}, "
                f"nomi={len(names)}, squadre={len(clubs)}, "
                f"primi nomi={names[:5]}, "
                f"prime squadre={clubs[:5]}"
            )

        costs: list[int] = []

        while (
            index < len(tokens)
            and len(costs) < len(names)
            and is_cost(tokens[index])
        ):
            costs.append(int(tokens[index]))
            index += 1

        if len(costs) != len(names):
            raise RuntimeError(
                "Numero costi diverso dal numero giocatori. "
                f"Ruolo={current_role}, "
                f"nomi={len(names)}, costi={len(costs)}, "
                f"prossimi token={tokens[index:index + 10]}"
            )

        for name, club, cost in zip(
            names,
            clubs,
            costs,
            strict=True,
        ):
            role_counters[current_role] += 1

            external_id = (
                f"GAZZETTA-2026-{current_role}-"
                f"{role_counters[current_role]:04d}"
            )

            normalized_name = normalize_player_name(
                external_id,
                name,
            )

            players.append(
                PlayerRow(
                    external_id=external_id,
                    name=normalized_name,
                    club=club,
                    role=current_role,
                    quotation=cost,
                )
            )

        diagnostics.append(
            f"Blocco acquisito: ruolo={current_role}, "
            f"giocatori={len(names)}"
        )

    return players, diagnostics


def validate_players(
    players: list[PlayerRow],
) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []

    if not players:
        errors.append("Nessun giocatore estratto")

    external_ids: set[str] = set()
    player_occurrences: dict[
        tuple[str, str],
        list[PlayerRow],
    ] = {}

    for row_number, player in enumerate(players, start=1):
        if player.external_id in external_ids:
            errors.append(
                f"ID duplicato alla riga {row_number}: "
                f"{player.external_id}"
            )

        external_ids.add(player.external_id)

        key = (
            player.name.casefold(),
            player.club.casefold(),
        )

        player_occurrences.setdefault(
            key,
            [],
        ).append(player)

        if player.club not in CLUBS:
            errors.append(
                f"Squadra non valida alla riga {row_number}: "
                f"{player.club}"
            )

        if player.role not in {"P", "D", "C", "A"}:
            errors.append(
                f"Ruolo non valido alla riga {row_number}: "
                f"{player.role}"
            )

        if player.quotation < 0:
            errors.append(
                f"Quotazione negativa alla riga {row_number}: "
                f"{player.quotation}"
            )

    for occurrences in player_occurrences.values():
        if len(occurrences) <= 1:
            continue

        first = occurrences[0]

        details = ", ".join(
            f"{player.external_id}/{player.role}/"
            f"{player.quotation}"
            for player in occurrences
        )

        warnings.append(
            f"Omonimia da verificare: "
            f"{first.name} - {first.club} -> {details}"
        )

    return errors, warnings


def write_csv(players: list[PlayerRow]) -> None:
    OUTPUT_PATH.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    with OUTPUT_PATH.open(
        "w",
        encoding="utf-8",
        newline="",
    ) as csv_file:
        writer = csv.DictWriter(
            csv_file,
            fieldnames=[
                "external_id",
                "name",
                "club",
                "role",
                "quotation",
                "fmv",
            ],
        )

        writer.writeheader()

        for player in players:
            writer.writerow(
                {
                    "external_id": player.external_id,
                    "name": player.name,
                    "club": player.club,
                    "role": player.role,
                    "quotation": player.quotation,
                    "fmv": "",
                }
            )


def write_report(
    players: list[PlayerRow],
    diagnostics: list[str],
    errors: list[str],
    warnings: list[str],
) -> None:
    REPORT_PATH.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    role_counts = {
        role: sum(
            1
            for player in players
            if player.role == role
        )
        for role in ("P", "D", "C", "A")
    }

    club_counts = {
        club: sum(
            1
            for player in players
            if player.club == club
        )
        for club in sorted(CLUBS)
    }

    lines = [
        "GAZZETTA 2026/2027 - REPORT NORMALIZZAZIONE",
        "=" * 55,
        f"PDF: {PDF_PATH}",
        f"CSV: {OUTPUT_PATH}",
        f"Giocatori totali: {len(players)}",
        "",
        "CONTEGGIO PER RUOLO",
        f"P: {role_counts['P']}",
        f"D: {role_counts['D']}",
        f"C: {role_counts['C']}",
        f"A: {role_counts['A']}",
        "",
        "CONTEGGIO PER SQUADRA",
    ]

    for club, count in club_counts.items():
        lines.append(f"{club}: {count}")

    lines.extend(
        [
            "",
            "DIAGNOSTICA PARSER",
            *diagnostics,
            "",
            "AVVISI",
        ]
    )

    if warnings:
        lines.extend(warnings)
    else:
        lines.append("Nessun avviso")

    lines.extend(
        [
            "",
            "ERRORI",
        ]
    )

    if errors:
        lines.extend(errors)
    else:
        lines.append("Nessun errore")

    REPORT_PATH.write_text(
        "\n".join(lines) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    if not PDF_PATH.exists():
        print(
            f"ERRORE: PDF non trovato: {PDF_PATH}",
            file=sys.stderr,
        )
        return 1

    reader = PdfReader(PDF_PATH)

    print(f"PDF: {PDF_PATH}")
    print(f"Pagine: {len(reader.pages)}")

    tokens = extract_tokens(reader)
    players, diagnostics = parse_players(tokens)
    errors, warnings = validate_players(players)

    write_report(
        players,
        diagnostics,
        errors,
        warnings,
    )

    if errors:
        print()
        print("NORMALIZZAZIONE FALLITA")

        for error in errors:
            print(f"- {error}")

        print(f"Report: {REPORT_PATH}")
        return 1

    write_csv(players)

    print()
    print("NORMALIZZAZIONE COMPLETATA")
    print(f"Giocatori: {len(players)}")
    print(f"CSV: {OUTPUT_PATH}")
    print(f"Report: {REPORT_PATH}")
    print(f"Avvisi: {len(warnings)}")

    for warning in warnings:
        print(f"- {warning}")

    for role in ("P", "D", "C", "A"):
        count = sum(
            1
            for player in players
            if player.role == role
        )

        print(f"Ruolo {role}: {count}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())