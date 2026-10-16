from __future__ import annotations

import argparse
import csv
import hashlib
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path


REQUIRED_COLUMNS = (
    "external_id",
    "name",
    "club",
    "role",
)

OPTIONAL_COLUMNS = (
    "quotation",
    "fmv",
)

VALID_ROLES = {
    "P",
    "D",
    "C",
    "A",
}

VALID_SOURCES = {
    "FANTACALCIO",
    "GAZZETTA",
    "FANTAMARKT",
}

VALID_CLUBS = {
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

SEASON_PATTERN = re.compile(
    r"^\d{4}/\d{4}$"
)

INTEGER_PATTERN = re.compile(
    r"^\d+$"
)


@dataclass(frozen=True)
class ValidationResult:
    file: str
    source: str
    season: str
    sha256: str
    valid: bool
    total_rows: int
    valid_rows: int
    invalid_rows: int
    role_counts: dict[str, int]
    club_counts: dict[str, int]
    errors: list[str]
    warnings: list[str]


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Valida un CSV normalizzato prima "
            "dell'import nel backend."
        )
    )

    parser.add_argument(
        "--file",
        required=True,
        help="Percorso del CSV da validare",
    )

    parser.add_argument(
        "--source",
        required=True,
        help="FANTACALCIO, GAZZETTA o FANTAMARKT",
    )

    parser.add_argument(
        "--season",
        required=True,
        help="Stagione nel formato 2026/2027",
    )

    parser.add_argument(
        "--report",
        help="Percorso facoltativo del report JSON",
    )

    return parser.parse_args()


def calculate_sha256(path: Path) -> str:
    digest = hashlib.sha256()

    with path.open("rb") as file_handle:
        while chunk := file_handle.read(1024 * 1024):
            digest.update(chunk)

    return digest.hexdigest()


def clean(value: str | None) -> str:
    if value is None:
        return ""

    return " ".join(value.strip().split())


def validate_numeric_field(
    value: str,
    field_name: str,
    row_number: int,
    errors: list[str],
) -> None:
    if not value:
        return

    if not INTEGER_PATTERN.fullmatch(value):
        errors.append(
            f"Riga {row_number}: "
            f"{field_name} non numerico: {value}"
        )
        return

    if int(value) < 0:
        errors.append(
            f"Riga {row_number}: "
            f"{field_name} negativo: {value}"
        )


def validate_file(
    path: Path,
    source: str,
    season: str,
) -> ValidationResult:
    errors: list[str] = []
    warnings: list[str] = []

    source = source.upper().strip()
    season = season.strip()

    if source not in VALID_SOURCES:
        errors.append(
            f"Fonte non valida: {source}"
        )

    if not SEASON_PATTERN.fullmatch(season):
        errors.append(
            f"Stagione non valida: {season}"
        )

    if not path.exists():
        errors.append(
            f"File non trovato: {path}"
        )

        return ValidationResult(
            file=str(path),
            source=source,
            season=season,
            sha256="",
            valid=False,
            total_rows=0,
            valid_rows=0,
            invalid_rows=0,
            role_counts={},
            club_counts={},
            errors=errors,
            warnings=warnings,
        )

    if not path.is_file():
        errors.append(
            f"Il percorso non è un file: {path}"
        )

    if path.suffix.casefold() != ".csv":
        errors.append(
            f"Il file non è CSV: {path.name}"
        )

    if path.stat().st_size == 0:
        errors.append(
            "Il file è vuoto"
        )

    if errors:
        return ValidationResult(
            file=str(path),
            source=source,
            season=season,
            sha256=calculate_sha256(path)
            if path.exists() and path.is_file()
            else "",
            valid=False,
            total_rows=0,
            valid_rows=0,
            invalid_rows=0,
            role_counts={},
            club_counts={},
            errors=errors,
            warnings=warnings,
        )

    total_rows = 0
    invalid_row_numbers: set[int] = set()
    role_counter: Counter[str] = Counter()
    club_counter: Counter[str] = Counter()
    external_ids: set[str] = set()
    player_occurrences: Counter[
        tuple[str, str, str]
    ] = Counter()

    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as csv_file:
        reader = csv.DictReader(csv_file)

        headers = tuple(
            clean(header)
            for header in (reader.fieldnames or [])
        )

        missing_headers = [
            column
            for column in REQUIRED_COLUMNS
            if column not in headers
        ]

        if missing_headers:
            errors.append(
                "Colonne obbligatorie mancanti: "
                + ", ".join(missing_headers)
            )

            return ValidationResult(
                file=str(path),
                source=source,
                season=season,
                sha256=calculate_sha256(path),
                valid=False,
                total_rows=0,
                valid_rows=0,
                invalid_rows=0,
                role_counts={},
                club_counts={},
                errors=errors,
                warnings=warnings,
            )

        for row_number, row in enumerate(
            reader,
            start=2,
        ):
            total_rows += 1
            row_errors_before = len(errors)

            external_id = clean(
                row.get("external_id")
            )
            name = clean(
                row.get("name")
            )
            club = clean(
                row.get("club")
            )
            role = clean(
                row.get("role")
            ).upper()
            quotation = clean(
                row.get("quotation")
            )
            fmv = clean(
                row.get("fmv")
            )

            if not external_id:
                errors.append(
                    f"Riga {row_number}: "
                    "external_id mancante"
                )

            if not name:
                errors.append(
                    f"Riga {row_number}: "
                    "name mancante"
                )

            if not club:
                errors.append(
                    f"Riga {row_number}: "
                    "club mancante"
                )

            if not role:
                errors.append(
                    f"Riga {row_number}: "
                    "role mancante"
                )

            if role and role not in VALID_ROLES:
                errors.append(
                    f"Riga {row_number}: "
                    f"ruolo non valido: {role}"
                )

            if club and club not in VALID_CLUBS:
                errors.append(
                    f"Riga {row_number}: "
                    f"squadra non valida: {club}"
                )

            if external_id in external_ids:
                errors.append(
                    f"Riga {row_number}: "
                    f"external_id duplicato: {external_id}"
                )

            external_ids.add(external_id)

            validate_numeric_field(
                quotation,
                "quotation",
                row_number,
                errors,
            )

            validate_numeric_field(
                fmv,
                "fmv",
                row_number,
                errors,
            )

            if role in VALID_ROLES:
                role_counter[role] += 1

            if club in VALID_CLUBS:
                club_counter[club] += 1

            if name and club and role:
                player_occurrences[
                    (
                        name.casefold(),
                        club.casefold(),
                        role,
                    )
                ] += 1

            if len(errors) > row_errors_before:
                invalid_row_numbers.add(
                    row_number
                )

    if total_rows == 0:
        errors.append(
            "Il CSV non contiene righe dati"
        )

    for key, count in player_occurrences.items():
        if count <= 1:
            continue

        name, club, role = key

        warnings.append(
            "Possibile omonimia o duplicato: "
            f"{name} - {club} - {role} "
            f"({count} occorrenze)"
        )

    missing_clubs = sorted(
        VALID_CLUBS - set(club_counter)
    )

    if missing_clubs:
        warnings.append(
            "Squadre senza giocatori nel CSV: "
            + ", ".join(missing_clubs)
        )

    missing_roles = sorted(
        VALID_ROLES - set(role_counter)
    )

    if missing_roles:
        warnings.append(
            "Ruoli assenti nel CSV: "
            + ", ".join(missing_roles)
        )

    invalid_rows = len(
        invalid_row_numbers
    )

    valid_rows = (
        total_rows - invalid_rows
    )

    return ValidationResult(
        file=str(path.resolve()),
        source=source,
        season=season,
        sha256=calculate_sha256(path),
        valid=not errors,
        total_rows=total_rows,
        valid_rows=valid_rows,
        invalid_rows=invalid_rows,
        role_counts=dict(
            sorted(role_counter.items())
        ),
        club_counts=dict(
            sorted(club_counter.items())
        ),
        errors=errors,
        warnings=warnings,
    )


def print_result(
    result: ValidationResult,
) -> None:
    print()
    print("=" * 70)
    print("VALIDAZIONE CSV")
    print("=" * 70)
    print(f"File: {result.file}")
    print(f"Fonte: {result.source}")
    print(f"Stagione: {result.season}")
    print(f"SHA-256: {result.sha256}")
    print(f"Righe totali: {result.total_rows}")
    print(f"Righe valide: {result.valid_rows}")
    print(f"Righe non valide: {result.invalid_rows}")
    print()

    print("RUOLI")

    for role in ("P", "D", "C", "A"):
        print(
            f"{role}: "
            f"{result.role_counts.get(role, 0)}"
        )

    print()
    print("SQUADRE")

    for club, count in result.club_counts.items():
        print(f"{club}: {count}")

    print()
    print("AVVISI")

    if result.warnings:
        for warning in result.warnings:
            print(f"- {warning}")
    else:
        print("Nessun avviso")

    print()
    print("ERRORI")

    if result.errors:
        for error in result.errors:
            print(f"- {error}")
    else:
        print("Nessun errore")

    print()

    if result.valid:
        print("ESITO: VALIDAZIONE SUPERATA")
    else:
        print("ESITO: VALIDAZIONE FALLITA")


def write_report(
    result: ValidationResult,
    report_path: Path,
) -> None:
    report_path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    report_path.write_text(
        json.dumps(
            result.__dict__,
            ensure_ascii=False,
            indent=2,
        )
        + "\n",
        encoding="utf-8",
    )


def main() -> int:
    args = parse_arguments()

    result = validate_file(
        Path(args.file),
        args.source,
        args.season,
    )

    print_result(result)

    if args.report:
        report_path = Path(args.report)

        write_report(
            result,
            report_path,
        )

        print(f"Report JSON: {report_path.resolve()}")

    return 0 if result.valid else 1


if __name__ == "__main__":
    raise SystemExit(main())