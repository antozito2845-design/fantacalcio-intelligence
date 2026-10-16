from __future__ import annotations

import argparse
import base64
import csv
import json
import re
import sys
import unicodedata
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


SCRIPT_ROOT = Path(__file__).resolve().parent
DEFAULT_FANTACALCIO = (
    SCRIPT_ROOT / "output" / "players-fantacalcio-2026-2027.csv"
)
DEFAULT_GAZZETTA = (
    SCRIPT_ROOT / "output" / "players-gazzetta-2026-2027.csv"
)
DEFAULT_FANTAMARKT = (
    SCRIPT_ROOT / "output" / "players-fantamarkt-2026-2027.csv"
)
DEFAULT_REPORT = (
    SCRIPT_ROOT / "logs" / "reconciliation-candidate-report.csv"
)

STOPWORDS = {
    "fc",
    "calcio",
    "ac",
    "as",
    "ssc",
    "ss",
    "us",
}


@dataclass(frozen=True)
class PlayerRow:
    external_id: str
    name: str
    normalized_name: str
    club: str
    normalized_club: str
    role: str
    source: str


@dataclass(frozen=True)
class Candidate:
    source: str
    external_id: str
    name: str
    club: str
    role: str
    score: int
    name_similarity: float
    same_club: bool
    same_role: bool
    surname_overlap: bool
    exact_name: bool
    reverse_conflicts: int
    reason: str


def normalize_text(value: str | None) -> str:
    if not value:
        return ""

    value = unicodedata.normalize("NFKD", value)
    value = "".join(
        char
        for char in value
        if not unicodedata.combining(char)
    )
    value = value.casefold()
    value = value.replace("'", " ")
    value = value.replace("-", " ")
    value = re.sub(r"[^a-z0-9\s]", " ", value)
    value = re.sub(r"\s+", " ", value).strip()
    return value


def normalized_tokens(value: str | None) -> list[str]:
    return [
        token
        for token in normalize_text(value).split()
        if token and token not in STOPWORDS
    ]


def base_tokens_for_abbreviation(value: str | None) -> list[str]:
    if not value:
        return []

    raw_tokens = value.strip().split()
    kept: list[str] = []

    for token in raw_tokens:
        cleaned = token.strip()

        if re.fullmatch(
            r"(?iu)[\wÀ-ÿ]{1,3}(?:\.[\wÀ-ÿ]{1,3})*\.",
            cleaned,
        ):
            break

        kept.append(cleaned)

    return normalized_tokens(" ".join(kept))


def surname_token_set(value: str | None) -> set[str]:
    tokens = normalized_tokens(value)
    if not tokens:
        return set()

    # We do not assume Western first-name/last-name ordering.
    # All non-abbreviation lexical tokens are eligible for overlap.
    return set(tokens)


def load_csv(path: Path, source: str) -> list[PlayerRow]:
    if not path.exists():
        return []

    rows: list[PlayerRow] = []

    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as handle:
        reader = csv.DictReader(handle)

        required = {
            "external_id",
            "name",
            "club",
            "role",
        }

        missing = required - set(reader.fieldnames or [])
        if missing:
            raise RuntimeError(
                f"{path.name}: colonne mancanti: "
                + ", ".join(sorted(missing))
            )

        for raw in reader:
            external_id = (raw.get("external_id") or "").strip()
            name = (raw.get("name") or "").strip()
            club = (raw.get("club") or "").strip()
            role = (raw.get("role") or "").strip().upper()

            if not external_id or not name:
                continue

            rows.append(
                PlayerRow(
                    external_id=external_id,
                    name=name,
                    normalized_name=normalize_text(name),
                    club=club,
                    normalized_club=normalize_text(club),
                    role=role,
                    source=source,
                )
            )

    return rows


def build_basic_auth(username: str, password: str) -> str:
    token = base64.b64encode(
        f"{username}:{password}".encode("utf-8")
    ).decode("ascii")

    return f"Basic {token}"


def load_open_reviews(
    base_url: str,
    season: str,
    username: str,
    password: str,
) -> list[dict]:
    query = urlencode(
        {
            "season": season,
            "status": "OPEN",
        }
    )

    url = (
        base_url.rstrip("/")
        + "/api/v1/admin/reconciliation/review?"
        + query
    )

    request = Request(
        url,
        headers={
            "Authorization": build_basic_auth(
                username,
                password,
            ),
            "Accept": "application/json",
        },
    )

    try:
        with urlopen(request, timeout=30) as response:
            body = response.read().decode(
                "utf-8",
                errors="replace",
            )
    except HTTPError as exc:
        details = exc.read().decode(
            "utf-8",
            errors="replace",
        )
        raise RuntimeError(
            f"Review API HTTP {exc.code}: {details}"
        ) from exc
    except URLError as exc:
        raise RuntimeError(
            f"Review API non raggiungibile: {exc.reason}"
        ) from exc

    payload = json.loads(body)

    if not isinstance(payload, list):
        raise RuntimeError(
            "La review API non ha restituito una lista"
        )

    return payload


def score_candidate(
    fantacalcio: PlayerRow,
    secondary: PlayerRow,
    fantacalcio_rows: list[PlayerRow],
) -> Candidate:
    exact_name = (
        fantacalcio.normalized_name
        == secondary.normalized_name
    )

    same_club = bool(
        fantacalcio.normalized_club
        and fantacalcio.normalized_club
        == secondary.normalized_club
    )

    same_role = bool(
        fantacalcio.role
        and secondary.role
        and fantacalcio.role == secondary.role
    )

    similarity = SequenceMatcher(
        None,
        fantacalcio.normalized_name,
        secondary.normalized_name,
    ).ratio()

    fc_tokens = surname_token_set(fantacalcio.name)
    sec_tokens = surname_token_set(secondary.name)
    overlap = fc_tokens & sec_tokens
    surname_overlap = bool(overlap)

    fc_base = set(
        base_tokens_for_abbreviation(
            fantacalcio.name
        )
    )
    sec_base = set(
        base_tokens_for_abbreviation(
            secondary.name
        )
    )

    abbreviation_overlap = bool(fc_base & sec_base)

    secondary_base = set(
        base_tokens_for_abbreviation(
            secondary.name
        )
    )

    reverse_conflicts = 0

    if secondary_base:
        reverse_conflicts = sum(
            1
            for other in fantacalcio_rows
            if (
                other.normalized_club
                == fantacalcio.normalized_club
                and bool(
                    set(
                        base_tokens_for_abbreviation(
                            other.name
                        )
                    )
                    & secondary_base
                )
            )
        )

    score = round(similarity * 55)

    reasons: list[str] = [
        f"name={similarity:.3f}",
    ]

    if exact_name:
        score += 30
        reasons.append("nome_esatto")

    if same_club:
        score += 20
        reasons.append("stesso_club")

    if same_role:
        score += 10
        reasons.append("stesso_ruolo")

    if surname_overlap:
        score += 10
        reasons.append(
            "token_nome="
            + ",".join(sorted(overlap))
        )

    if abbreviation_overlap:
        score += 5
        reasons.append("base_abbreviazione")

    if reverse_conflicts > 1:
        score -= 35
        reasons.append(
            f"reverse_conflicts={reverse_conflicts}"
        )

    if (
        fantacalcio.normalized_club
        and secondary.normalized_club
        and not same_club
    ):
        score -= 12
        reasons.append("club_diverso")

    if (
        fantacalcio.role
        and secondary.role
        and not same_role
    ):
        score -= 8
        reasons.append("ruolo_diverso")

    score = max(0, min(score, 100))

    return Candidate(
        source=secondary.source,
        external_id=secondary.external_id,
        name=secondary.name,
        club=secondary.club,
        role=secondary.role,
        score=score,
        name_similarity=similarity,
        same_club=same_club,
        same_role=same_role,
        surname_overlap=surname_overlap,
        exact_name=exact_name,
        reverse_conflicts=reverse_conflicts,
        reason="; ".join(reasons),
    )


def candidates_for(
    fantacalcio: PlayerRow,
    secondary_rows: list[PlayerRow],
    fantacalcio_rows: list[PlayerRow],
    limit: int,
) -> list[Candidate]:
    scored = [
        score_candidate(
            fantacalcio,
            secondary,
            fantacalcio_rows,
        )
        for secondary in secondary_rows
    ]

    useful = [
        candidate
        for candidate in scored
        if (
            candidate.score >= 35
            or candidate.surname_overlap
            or candidate.exact_name
        )
    ]

    useful.sort(
        key=lambda candidate: (
            -candidate.score,
            -candidate.name_similarity,
            candidate.name.casefold(),
            candidate.external_id,
        )
    )

    return useful[:limit]


def confidence_label(
    candidates: list[Candidate],
) -> str:
    if not candidates:
        return "NONE"

    first = candidates[0]
    second_score = (
        candidates[1].score
        if len(candidates) > 1
        else 0
    )

    margin = first.score - second_score

    if first.reverse_conflicts > 1:
        return "AMBIGUOUS"

    if (
        first.exact_name
        and first.same_club
        and first.score >= 90
        and margin >= 10
    ):
        return "VERY_STRONG"

    if (
        first.score >= 82
        and first.same_club
        and margin >= 12
    ):
        return "STRONG"

    if first.score >= 65:
        return "REVIEW"

    return "WEAK"


def write_report(
    output: Path,
    reviews: list[dict],
    fantacalcio_by_id: dict[str, PlayerRow],
    fantacalcio_rows: list[PlayerRow],
    sources: dict[str, list[PlayerRow]],
    limit: int,
) -> tuple[int, int]:
    output.parent.mkdir(
        parents=True,
        exist_ok=True,
    )

    unresolved_count = 0
    candidate_rows = 0

    with output.open(
        "w",
        encoding="utf-8",
        newline="",
    ) as handle:
        writer = csv.writer(handle)

        writer.writerow(
            [
                "review_id",
                "fantacalcio_external_id",
                "fantacalcio_name",
                "fantacalcio_club",
                "fantacalcio_role",
                "source",
                "rank",
                "candidate_external_id",
                "candidate_name",
                "candidate_club",
                "candidate_role",
                "score",
                "confidence",
                "name_similarity",
                "same_club",
                "same_role",
                "reverse_conflicts",
                "reason",
            ]
        )

        for review in reviews:
            external_id = str(
                review.get(
                    "fantacalcioExternalId",
                    "",
                )
            ).strip()

            fc = fantacalcio_by_id.get(
                external_id
            )

            if fc is None:
                # Compatibility fallback if Jackson naming differs.
                external_id = str(
                    review.get(
                        "fantacalcio_external_id",
                        external_id,
                    )
                ).strip()
                fc = fantacalcio_by_id.get(
                    external_id
                )

            if fc is None:
                print(
                    "ATTENZIONE: Fantacalcio ID "
                    f"non trovato nel CSV: {external_id}",
                    file=sys.stderr,
                )
                continue

            unresolved_count += 1

            for source_name, rows in sources.items():
                candidates = candidates_for(
                    fc,
                    rows,
                    fantacalcio_rows,
                    limit,
                )

                confidence = confidence_label(
                    candidates
                )

                if not candidates:
                    writer.writerow(
                        [
                            review.get("id", ""),
                            fc.external_id,
                            fc.name,
                            fc.club,
                            fc.role,
                            source_name,
                            "",
                            "",
                            "",
                            "",
                            "",
                            "",
                            "NONE",
                            "",
                            "",
                            "",
                            "",
                            "nessun_candidato_utile",
                        ]
                    )
                    continue

                for rank, candidate in enumerate(
                    candidates,
                    start=1,
                ):
                    candidate_rows += 1

                    writer.writerow(
                        [
                            review.get("id", ""),
                            fc.external_id,
                            fc.name,
                            fc.club,
                            fc.role,
                            source_name,
                            rank,
                            candidate.external_id,
                            candidate.name,
                            candidate.club,
                            candidate.role,
                            candidate.score,
                            confidence
                            if rank == 1
                            else "",
                            f"{candidate.name_similarity:.3f}",
                            str(candidate.same_club).lower(),
                            str(candidate.same_role).lower(),
                            candidate.reverse_conflicts,
                            candidate.reason,
                        ]
                    )

    return unresolved_count, candidate_rows


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Genera candidati secondari per gli elementi OPEN "
            "della reconciliation review senza modificare il database."
        )
    )

    parser.add_argument(
        "--base-url",
        default="http://localhost:8080",
    )
    parser.add_argument(
        "--season",
        default="2026/2027",
    )
    parser.add_argument(
        "--username",
        required=True,
    )
    parser.add_argument(
        "--password",
        required=True,
    )
    parser.add_argument(
        "--fantacalcio",
        type=Path,
        default=DEFAULT_FANTACALCIO,
    )
    parser.add_argument(
        "--gazzetta",
        type=Path,
        default=DEFAULT_GAZZETTA,
    )
    parser.add_argument(
        "--fantamarkt",
        type=Path,
        default=DEFAULT_FANTAMARKT,
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_REPORT,
    )
    parser.add_argument(
        "--limit",
        type=int,
        default=5,
    )

    return parser.parse_args()


def main() -> int:
    args = parse_arguments()

    if args.limit < 1 or args.limit > 20:
        print(
            "--limit deve essere tra 1 e 20",
            file=sys.stderr,
        )
        return 2

    fantacalcio_rows = load_csv(
        args.fantacalcio,
        "FANTACALCIO",
    )

    if not fantacalcio_rows:
        print(
            f"CSV Fantacalcio vuoto/non trovato: {args.fantacalcio}",
            file=sys.stderr,
        )
        return 2

    gazzetta_rows = load_csv(
        args.gazzetta,
        "GAZZETTA",
    )

    if not gazzetta_rows:
        print(
            f"CSV Gazzetta vuoto/non trovato: {args.gazzetta}",
            file=sys.stderr,
        )
        return 2

    sources = {
        "GAZZETTA": gazzetta_rows,
    }

    fantamarkt_rows = load_csv(
        args.fantamarkt,
        "FANTAMARKT",
    )

    if fantamarkt_rows:
        sources["FANTAMARKT"] = fantamarkt_rows
    else:
        print(
            "Fantamarkt non disponibile: "
            "analisi eseguita solo su Gazzetta."
        )

    try:
        reviews = load_open_reviews(
            args.base_url,
            args.season,
            args.username,
            args.password,
        )
    except Exception as exc:
        print(
            f"ERRORE: {type(exc).__name__}: {exc}",
            file=sys.stderr,
        )
        return 1

    fantacalcio_by_id = {
        row.external_id: row
        for row in fantacalcio_rows
    }

    unresolved_count, candidate_rows = write_report(
        args.output,
        reviews,
        fantacalcio_by_id,
        fantacalcio_rows,
        sources,
        args.limit,
    )

    print("")
    print("RECONCILIATION CANDIDATE REPORT COMPLETATO")
    print(f"Review OPEN analizzate: {unresolved_count}")
    print(
        "Fonti secondarie disponibili: "
        + ", ".join(sources)
    )
    print(f"Candidati scritti: {candidate_rows}")
    print(f"Report: {args.output}")
    print("")
    print(
        "IMPORTANTE: questo report NON crea link e "
        "NON modifica il database."
    )

    return 0


if __name__ == "__main__":
    sys.exit(main())
