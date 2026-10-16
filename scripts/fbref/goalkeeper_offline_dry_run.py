#!/usr/bin/env python3
"""Offline FBref goalkeeper catalog matching and dry-run validation.

This module intentionally has no network, database, or subprocess dependency. It accepts
CSV snapshots obtained separately (for example, by a person through an authorized export),
starts from the internal goalkeeper catalog, and emits JSON to stdout unless an explicit
output path is supplied. It never imports data into PostgreSQL.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
import unicodedata
from datetime import datetime
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, TextIO
from urllib.parse import urlparse

SOURCE = "FBREF"
METRICS = (
    "appearances",
    "starts",
    "minutes_played",
    "goals_conceded",
    "saves",
    "clean_sheets",
    "penalties_saved",
)

FIELD_ALIASES = {
    "external_id": (
        "external_id",
        "fbref_external_id",
        "fbref_id",
        "source_external_id",
    ),
    "name": ("name", "player", "player_name", "full_name"),
    "nationality": ("nationality", "nation", "country"),
    "club": ("club", "squad", "team", "current_club"),
    "position": ("position", "pos", "player_position"),
    "source_url": ("source_url", "page_url", "origin_url", "url", "player_url"),
    "observed_at": ("observed_at", "source_observed_at", "acquired_at", "observation_time"),
    "source": ("source", "provider", "source_name"),
    "season": ("season", "season_code", "reference_season"),
    "player_id": (
        "player_id",
        "internal_player_id",
        "existing_player_id",
        "resolved_player_id",
        "candidate_player_id",
    ),
    "role": ("role", "fantasy_role", "current_role", "role_code"),
    "fbref_url": ("fbref_url", "fbref_player_url", "player_url"),
}

METRIC_ALIASES = {
    "appearances": ("appearances", "mp", "games_mp", "matches_played"),
    "starts": ("starts", "games_starts"),
    "minutes_played": ("minutes_played", "min", "minutes", "games_minutes"),
    "goals_conceded": ("goals_conceded", "ga", "goals_conceded_total"),
    "saves": ("saves", "goalkeeper_saves"),
    "clean_sheets": ("clean_sheets", "cs"),
    "penalties_saved": ("penalties_saved", "pksv", "pk_sv", "penalty_saved"),
}

MISSING_VALUES = {"", "-", "—", "null", "none", "n/a", "na"}


@dataclass(frozen=True)
class CatalogPlayer:
    player_id: str
    name: str
    club: str
    nationality: str
    fbref_external_id: str
    row_number: int

    @property
    def normalized_name(self) -> str:
        return normalize_text(self.name)

    @property
    def normalized_club(self) -> str:
        return normalize_text(self.club)

    @property
    def normalized_nationality(self) -> str:
        return normalize_text(self.nationality)

    @property
    def surname(self) -> str:
        tokens = self.normalized_name.split()
        return tokens[-1] if tokens else ""


@dataclass
class InputRecord:
    season: str
    external_id: str
    name: str
    club: str
    nationality: str
    position: str
    source_url: str
    observed_at: str
    origin: str
    metrics: dict[str, int | None]
    row_number: int
    errors: list[str]
    warnings: list[str]

    @property
    def normalized_name(self) -> str:
        return normalize_text(self.name)

    @property
    def normalized_club(self) -> str:
        return normalize_text(self.club)

    @property
    def normalized_nationality(self) -> str:
        return normalize_text(self.nationality)

    @property
    def surname(self) -> str:
        tokens = self.normalized_name.split()
        return tokens[-1] if tokens else ""


def normalize_text(value: object) -> str:
    if value is None:
        return ""
    text = unicodedata.normalize("NFKD", str(value).strip().lower())
    text = "".join(char for char in text if not unicodedata.combining(char))
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", text)).strip()


def canonical_header(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.strip().lower()).strip("_")


def get_value(row: dict[str, str], aliases: Iterable[str]) -> str:
    normalized = {canonical_header(key): value for key, value in row.items()}
    for alias in aliases:
        value = normalized.get(canonical_header(alias), "")
        if value is not None and str(value).strip() != "":
            return str(value).strip()
    return ""


def first_value(row: dict[str, str], key: str) -> str:
    return get_value(row, FIELD_ALIASES[key])


def extract_external_id(row: dict[str, str]) -> str:
    direct = first_value(row, "external_id")
    if direct:
        return direct
    url = first_value(row, "fbref_url") or first_value(row, "source_url")
    if not url:
        return ""
    path_parts = [part for part in urlparse(url).path.split("/") if part]
    try:
        index = path_parts.index("players")
    except ValueError:
        return ""
    return path_parts[index + 1] if index + 1 < len(path_parts) else ""


def normalize_season(value: str) -> str:
    value = value.strip()
    match = re.fullmatch(r"(\d{4})[/-](\d{4})", value)
    if match:
        return f"{match.group(1)}/{match.group(2)}"
    return value


def valid_observed_at(value: str) -> bool:
    try:
        datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
        return True
    except ValueError:
        return False


def parse_integer(value: str, field: str, errors: list[str]) -> int | None:
    normalized = value.strip().lower()
    if normalized in MISSING_VALUES:
        return None
    if not re.fullmatch(r"\d+|\d{1,3}(?:,\d{3})+", normalized):
        errors.append(f"INVALID_{field.upper()}")
        return None
    parsed = int(normalized.replace(",", ""))
    if parsed < 0:
        errors.append(f"NEGATIVE_{field.upper()}")
        return None
    return parsed


def parse_catalog_rows(stream: TextIO) -> tuple[list[CatalogPlayer], list[dict[str, object]]]:
    reader = csv.DictReader(stream)
    players: list[CatalogPlayer] = []
    invalid: list[dict[str, object]] = []
    for row_number, row in enumerate(reader, 2):
        player_id = first_value(row, "player_id")
        name = first_value(row, "name")
        role = first_value(row, "role").upper()
        errors: list[str] = []
        if not player_id:
            errors.append("MISSING_PLAYER_ID")
        if not name:
            errors.append("MISSING_PLAYER_NAME")
        if not role:
            errors.append("MISSING_ROLE")
        elif role != "P":
            continue
        if errors:
            invalid.append({"row_number": row_number, "status": "INVALID", "errors": errors, "raw": row})
            continue
        players.append(
            CatalogPlayer(
                player_id=player_id,
                name=name,
                club=first_value(row, "club"),
                nationality=first_value(row, "nationality"),
                fbref_external_id=extract_external_id(row),
                row_number=row_number,
            )
        )
    return players, invalid


def parse_mapping_rows(stream: TextIO) -> tuple[dict[str, set[str]], list[dict[str, object]]]:
    reader = csv.DictReader(stream)
    mappings: dict[str, set[str]] = defaultdict(set)
    invalid: list[dict[str, object]] = []
    for row_number, row in enumerate(reader, 2):
        source = first_value(row, "source").upper()
        if source and source != SOURCE:
            continue
        external_id = extract_external_id(row)
        player_id = first_value(row, "player_id")
        errors: list[str] = []
        if not external_id:
            errors.append("MISSING_FBREF_EXTERNAL_ID")
        if not player_id:
            errors.append("MISSING_PLAYER_ID")
        if errors:
            invalid.append({"row_number": row_number, "status": "INVALID", "errors": errors, "raw": row})
            continue
        mappings[external_id].add(player_id)
    return mappings, invalid


def parse_input_rows(
    stream: TextIO,
    default_season: str,
    observed_at: str,
    default_source_url: str,
    origin: str,
) -> list[InputRecord]:
    reader = csv.DictReader(stream)
    records: list[InputRecord] = []
    for row_number, row in enumerate(reader, 2):
        errors: list[str] = []
        warnings: list[str] = []
        season = normalize_season(first_value(row, "season") or default_season)
        external_id = extract_external_id(row)
        name = first_value(row, "name")
        source_url = first_value(row, "source_url") or default_source_url
        row_observed_at = first_value(row, "observed_at") or observed_at
        position = first_value(row, "position")
        if not season:
            errors.append("MISSING_SEASON")
        elif not re.fullmatch(r"\d{4}/\d{4}", season):
            errors.append("INVALID_SEASON")
        if not external_id:
            errors.append("MISSING_FBREF_EXTERNAL_ID")
        if not name:
            errors.append("MISSING_PLAYER_NAME")
        if not source_url:
            errors.append("MISSING_SOURCE_URL")
        if not row_observed_at:
            errors.append("MISSING_OBSERVED_AT")
        elif not valid_observed_at(row_observed_at):
            errors.append("INVALID_OBSERVED_AT")
        if position and normalize_text(position) not in {"gk", "goalkeeper", "goalkeepers", "portiere", "p"}:
            errors.append("NOT_A_GOALKEEPER_ROW")

        metrics: dict[str, int | None] = {}
        available_metric = False
        for metric, aliases in METRIC_ALIASES.items():
            raw_value = get_value(row, aliases)
            if raw_value:
                available_metric = True
            metrics[metric] = parse_integer(raw_value, metric, errors)
        if not available_metric:
            errors.append("NO_GOALKEEPER_METRIC_COLUMNS")
        if metrics["starts"] is not None and metrics["appearances"] is not None:
            if metrics["starts"] > metrics["appearances"]:
                errors.append("STARTS_EXCEED_APPEARANCES")

        records.append(
            InputRecord(
                season=season,
                external_id=external_id,
                name=name,
                club=first_value(row, "club"),
                nationality=first_value(row, "nationality"),
                position=position,
                source_url=source_url,
                observed_at=row_observed_at,
                origin=origin,
                metrics=metrics,
                row_number=row_number,
                errors=errors,
                warnings=warnings,
            )
        )
    return records


def _same_field(source: str, catalog: str) -> bool:
    if not source or not catalog:
        return False
    source_values = (part for part in source.split("|") if part.strip())
    return any(normalize_text(part) == normalize_text(catalog) for part in source_values)


def _evidence(record: InputRecord, player: CatalogPlayer) -> tuple[list[str], list[str]]:
    matched: list[str] = []
    conflicts: list[str] = []
    if record.normalized_club and player.normalized_club:
        (matched if _same_field(record.club, player.club) else conflicts).append("CLUB")
    if record.normalized_nationality and player.normalized_nationality:
        (matched if _same_field(record.nationality, player.nationality) else conflicts).append("NATIONALITY")
    return matched, conflicts


def match_record(
    record: InputRecord,
    players: list[CatalogPlayer],
    mappings: dict[str, set[str]],
) -> dict[str, object]:
    base: dict[str, object] = {
        "status": "INVALID" if record.errors else "UNMATCHED",
        "season": record.season,
        "source": SOURCE,
        "source_external_id": record.external_id,
        "source_url": record.source_url,
        "observed_at": record.observed_at,
        "origin": record.origin,
        "input_row": record.row_number,
        "input_name": record.name,
        "input_club": record.club,
        "input_nationality": record.nationality,
        **record.metrics,
        "candidate_player_id": "",
        "candidate_name": "",
        "candidate_club": "",
        "match_method": "",
        "match_confidence": "NONE",
        "reason": ";".join(record.errors) if record.errors else "",
        "warnings": list(record.warnings),
    }
    if record.errors:
        return base

    by_id = {player.player_id: player for player in players}
    mapped_ids = mappings.get(record.external_id, set())
    if mapped_ids:
        if len(mapped_ids) > 1:
            base.update(
                status="REVIEW",
                match_method="EXTERNAL_ID_COLLISION",
                match_confidence="LOW",
                reason="UNIQUE_FBREF_ID_POINTS_TO_MULTIPLE_CATALOG_PLAYERS",
                candidate_player_id=" | ".join(sorted(mapped_ids)),
            )
            return base
        mapped_id = next(iter(mapped_ids))
        player = by_id.get(mapped_id)
        if player is None:
            base.update(
                status="REVIEW",
                match_method="MAPPING_TARGET_NOT_IN_GOALKEEPER_CATALOG",
                match_confidence="LOW",
                reason="MAPPED_PLAYER_NOT_FOUND_OR_NOT_ROLE_P",
                candidate_player_id=mapped_id,
            )
            return base
        matched, conflicts = _evidence(record, player)
        if record.normalized_name and player.normalized_name and record.normalized_name != player.normalized_name:
            conflicts.append("FULL_NAME")
        base.update(
            candidate_player_id=player.player_id,
            candidate_name=player.name,
            candidate_club=player.club,
        )
        if conflicts:
            base.update(
                status="REVIEW",
                match_method="EXTERNAL_ID_MAPPING_CONFLICT",
                match_confidence="LOW",
                reason=";".join(f"MAPPING_CONFLICT_{item}" for item in conflicts),
            )
        else:
            base.update(
                status="MATCHED",
                match_method="EXTERNAL_ID_MAPPING",
                match_confidence="HIGH",
                reason="CERTIFIED_EXTERNAL_ID_MAPPING",
                warnings=[*record.warnings, *matched],
            )
        return base

    exact = [player for player in players if player.normalized_name == record.normalized_name]
    if len(exact) == 1:
        player = exact[0]
        matched, conflicts = _evidence(record, player)
        base.update(candidate_player_id=player.player_id, candidate_name=player.name, candidate_club=player.club)
        if conflicts:
            base.update(
                status="REVIEW",
                match_method="EXACT_NAME_EVIDENCE_CONFLICT",
                match_confidence="LOW",
                reason=";".join(f"EVIDENCE_CONFLICT_{item}" for item in conflicts),
            )
        elif matched:
            base.update(
                status="MATCHED",
                match_method="EXACT_NAME_" + "_".join(matched),
                match_confidence="MEDIUM",
                reason="EXACT_FULL_NAME_WITH_CATALOG_EVIDENCE",
                warnings=[*record.warnings, *matched],
            )
        else:
            base.update(
                status="REVIEW",
                match_method="EXACT_NAME_ONLY",
                match_confidence="MEDIUM",
                reason="FULL_NAME_REQUIRES_CLUB_OR_NATIONALITY_CONFIRMATION",
            )
        return base

    candidates = exact if len(exact) > 1 else [player for player in players if player.surname == record.surname and record.surname]
    if candidates:
        narrowed = []
        for player in candidates:
            matched, conflicts = _evidence(record, player)
            if matched and not conflicts:
                narrowed.append(player)
        chosen = narrowed if narrowed else candidates
        base.update(
            status="REVIEW",
            match_method="AMBIGUOUS_EXACT_NAME" if len(exact) > 1 else "SURNAME_FALLBACK",
            match_confidence="LOW",
            reason="NAME_NOT_UNIQUELY_VERIFIED;_MANUAL_REVIEW_REQUIRED",
            candidate_player_id=" | ".join(player.player_id for player in chosen),
            candidate_name=" | ".join(player.name for player in chosen),
            candidate_club=" | ".join(player.club for player in chosen),
        )
        return base

    base.update(status="UNMATCHED", match_method="NO_SAFE_CANDIDATE", reason="NO_NAME_OR_EXTERNAL_ID_CANDIDATE")
    return base


def aggregate_transfers(records: list[InputRecord]) -> tuple[list[InputRecord], list[dict[str, object]]]:
    grouped: dict[tuple[str, str], list[InputRecord]] = defaultdict(list)
    for record in records:
        if not record.errors:
            grouped[(record.external_id, record.season)].append(record)
    output: list[InputRecord] = [record for record in records if record.errors]
    invalid: list[dict[str, object]] = []
    for key, group in grouped.items():
        clubs = {normalize_text(record.club) for record in group if record.club}
        if len(group) == 1:
            output.append(group[0])
            continue
        if len(clubs) <= 1:
            for record in group:
                record.errors.append("DUPLICATE_EXTERNAL_ID_SEASON_CLUB")
                invalid.append({"status": "INVALID", "season": record.season, "source_external_id": record.external_id, "input_row": record.row_number, "reason": "DUPLICATE_EXTERNAL_ID_SEASON_CLUB"})
                output.append(record)
            continue
        first = group[0]
        metrics: dict[str, int | None] = {}
        name_values = {record.normalized_name for record in group if record.normalized_name}
        errors = ["MULTIPLE_NAMES_FOR_EXTERNAL_ID"] if len(name_values) > 1 else []
        warnings = ["MULTI_CLUB_AGGREGATION"]
        for metric in METRICS:
            values = [record.metrics[metric] for record in group]
            if all(value is not None for value in values):
                metrics[metric] = sum(value for value in values if value is not None)
            else:
                metrics[metric] = None
                warnings.append(f"PARTIAL_{metric.upper()}")
        output.append(
            InputRecord(
                season=first.season,
                external_id=first.external_id,
                name=first.name,
                club=" | ".join(record.club for record in group if record.club),
                nationality=first.nationality,
                position=first.position,
                source_url=" | ".join(dict.fromkeys(record.source_url for record in group if record.source_url)),
                observed_at=max(record.observed_at for record in group),
                origin="MULTI_CLUB_AGGREGATED",
                metrics=metrics,
                row_number=first.row_number,
                errors=errors,
                warnings=warnings,
            )
        )
    return output, invalid


def apply_player_external_id_collisions(results: list[dict[str, object]]) -> None:
    by_player_season: dict[tuple[str, str], set[str]] = defaultdict(set)
    for result in results:
        player_id = str(result.get("candidate_player_id", ""))
        if result["status"] == "MATCHED" and player_id:
            by_player_season[(player_id, str(result["season"]))].add(str(result["source_external_id"]))
    for result in results:
        key = (str(result.get("candidate_player_id", "")), str(result["season"]))
        if result["status"] == "MATCHED" and len(by_player_season[key]) > 1:
            result.update(
                status="REVIEW",
                match_method="PLAYER_SEASON_EXTERNAL_ID_COLLISION",
                match_confidence="LOW",
                reason="MULTIPLE_FBREF_EXTERNAL_IDS_FOR_PLAYER_SEASON",
            )


def build_coverage(
    players: list[CatalogPlayer],
    results: list[dict[str, object]],
    requested_seasons: Iterable[str],
) -> list[dict[str, object]]:
    coverage: list[dict[str, object]] = []
    for player in players:
        for season in requested_seasons:
            rows = [
                result
                for result in results
                if result["season"] == season and player.player_id in str(result.get("candidate_player_id", "")).split(" | ")
            ]
            if any(result["status"] == "MATCHED" for result in rows):
                status = "MATCHED"
                reason = "MATCHED_ROW_AVAILABLE"
            elif any(result["status"] == "REVIEW" for result in rows):
                status = "REVIEW"
                reason = "SEASON_ROW_REQUIRES_MANUAL_REVIEW"
            else:
                status = "UNMATCHED"
                reason = "NO_SEASON_ROW_FOR_CATALOG_GOALKEEPER"
            coverage.append(
                {
                    "player_id": player.player_id,
                    "player_name": player.name,
                    "season": season,
                    "status": status,
                    "reason": reason,
                }
            )
    return coverage


def run_dry_run(
    players: list[CatalogPlayer],
    records: list[InputRecord],
    mappings: dict[str, set[str]] | None = None,
    requested_seasons: Iterable[str] = (),
) -> dict[str, object]:
    mappings = mappings or {}
    effective_mappings: dict[str, set[str]] = defaultdict(set)
    for external_id, player_ids in mappings.items():
        effective_mappings[external_id].update(player_ids)
    for player in players:
        if player.fbref_external_id:
            effective_mappings[player.fbref_external_id].add(player.player_id)
    aggregated, duplicate_invalid = aggregate_transfers(records)
    results = [match_record(record, players, effective_mappings) for record in aggregated]
    apply_player_external_id_collisions(results)
    seasons = [normalize_season(season) for season in requested_seasons]
    if not seasons:
        seasons = sorted({str(result["season"]) for result in results if result["season"]})
    seasons = list(dict.fromkeys(seasons))
    coverage = build_coverage(players, results, seasons)
    counts: dict[str, int] = defaultdict(int)
    for result in results:
        counts[str(result["status"])] += 1
    return {
        "source": SOURCE,
        "records": results,
        "coverage": coverage,
        "invalid_duplicates": duplicate_invalid,
        "summary": {
            "catalog_goalkeepers": len(players),
            "input_records": len(records),
            "output_records": len(results),
            "matched": counts["MATCHED"],
            "review": counts["REVIEW"],
            "unmatched": counts["UNMATCHED"],
            "invalid": counts["INVALID"],
            "database_write": False,
            "network_access": False,
        },
    }


def open_csv(path: Path) -> TextIO:
    return path.open("r", encoding="utf-8-sig", newline="")


def parse_assignment(value: str, label: str) -> tuple[str, Path]:
    season, separator, path = value.partition("=")
    if not separator or not season or not path:
        raise argparse.ArgumentTypeError(f"{label} deve essere SEASON=PATH")
    return normalize_season(season), Path(path)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="FBref goalkeeper offline dry-run; no network and no DB")
    parser.add_argument("--catalog", type=Path, required=True, help="CSV catalogo interno; viene filtrato a ruolo P")
    parser.add_argument("--mapping", type=Path, help="CSV mapping FBREF external_id -> player_id già verificato")
    parser.add_argument("--season-file", action="append", type=lambda value: parse_assignment(value, "--season-file"), default=[])
    parser.add_argument("--history-file", action="append", type=lambda value: parse_assignment(value, "--history-file"), default=[])
    parser.add_argument("--season", action="append", dest="requested_seasons", default=[], help="stagione da includere nella coverage")
    parser.add_argument("--observed-at", required=True, help="timestamp UTC dell’acquisizione manuale/autorizzata")
    parser.add_argument("--source-url", default="", help="URL origine usato se non presente nella riga")
    parser.add_argument("--output", type=Path, help="JSON report; default stdout")
    args = parser.parse_args(argv)

    with open_csv(args.catalog) as stream:
        players, invalid_catalog = parse_catalog_rows(stream)
    mappings: dict[str, set[str]] = {}
    invalid_mapping: list[dict[str, object]] = []
    if args.mapping:
        with open_csv(args.mapping) as stream:
            mappings, invalid_mapping = parse_mapping_rows(stream)

    records: list[InputRecord] = []
    requested_seasons = [normalize_season(season) for season in args.requested_seasons]
    for origin, assignments in (("SEASON_TABLE", args.season_file), ("INDIVIDUAL_HISTORY", args.history_file)):
        for season, path in assignments:
            requested_seasons.append(season)
            with open_csv(path) as stream:
                records.extend(parse_input_rows(stream, season, args.observed_at, args.source_url, origin))

    report = run_dry_run(players, records, mappings, requested_seasons)
    report["invalid_catalog"] = invalid_catalog
    report["invalid_mapping"] = invalid_mapping
    rendered = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        sys.stdout.write(rendered)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
