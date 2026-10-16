import csv
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

FC_FILE = (
    ROOT
    / "data"
    / "identity"
    / "fantacalcio-serie-a-2026-2027.csv"
)

TM_FILE = (
    ROOT
    / "data"
    / "identity"
    / "transfermarkt-serie-a-2026-2027.csv"
)

CATALOG_FILE = (
    ROOT
    / "data"
    / "identity"
    / "catalog-transfermarkt-match-2026-2027.csv"
)

OUTPUT_DIR = (
    ROOT
    / "data"
    / "identity"
    / "comparison-2026-2027"
)

ALL_MATCHES_FILE = (
    OUTPUT_DIR
    / "fantacalcio-transfermarkt-all-matches.csv"
)

HIGH_FILE = (
    OUTPUT_DIR
    / "fantacalcio-transfermarkt-high.csv"
)

REVIEW_FILE = (
    OUTPUT_DIR
    / "fantacalcio-transfermarkt-review.csv"
)

UNMATCHED_FILE = (
    OUTPUT_DIR
    / "fantacalcio-transfermarkt-unmatched.csv"
)

DOB_CONFLICT_FILE = (
    OUTPUT_DIR
    / "fantacalcio-transfermarkt-dob-conflicts.csv"
)

ALIAS_HIGH_FILE = (
    OUTPUT_DIR
    / "fantacalcio-transfermarkt-alias-high.csv"
)

CATALOG_MISSING_FILE = (
    OUTPUT_DIR
    / "fantacalcio-not-in-operational-catalog.csv"
)

CATALOG_EXTRA_FILE = (
    OUTPUT_DIR
    / "operational-catalog-not-in-fantacalcio.csv"
)

REPORT_FILE = (
    OUTPUT_DIR
    / "fantacalcio-transfermarkt-comparison-report.txt"
)

OUTPUT_FIELDS = [
    "status",
    "match_rule",

    "fantacalcio_player_id",
    "fantacalcio_short_name",
    "fantacalcio_full_name",
    "fantacalcio_club",
    "fantasy_role",
    "fantacalcio_dob",
    "fantacalcio_height",
    "fantacalcio_nationality",
    "fantacalcio_foot",
    "fantacalcio_url",

    "transfermarkt_player_id",
    "transfermarkt_full_name",
    "transfermarkt_team",
    "transfermarkt_dob",
    "transfermarkt_height",
    "transfermarkt_nationality",
    "transfermarkt_url",

    "name_exact",
    "dob_match",
    "club_match",
    "height_match",
    "nationality_overlap",

    "candidate_count",
    "notes",
]


def read_csv(path):
    if not path.exists():
        raise RuntimeError(
            f"File non trovato:\n{path}"
        )

    with path.open(
        "r",
        encoding="utf-8-sig",
        newline="",
    ) as handle:
        return list(csv.DictReader(handle))


def write_csv(path, rows, fields):
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
            fieldnames=fields,
            extrasaction="ignore",
        )

        writer.writeheader()
        writer.writerows(rows)


def first_value(row, *names):
    for name in names:
        if name in row:
            value = (row.get(name) or "").strip()

            if value:
                return value

    return ""


def ascii_text(value):
    value = (value or "").strip().lower()

    value = (
        value
        .replace("’", "'")
        .replace("`", "'")
    )

    value = unicodedata.normalize(
        "NFKD",
        value,
    )

    value = "".join(
        char
        for char in value
        if not unicodedata.combining(char)
    )

    return value


def normalize_name(value):
    value = ascii_text(value)

    value = re.sub(
        r"[^a-z0-9]+",
        " ",
        value,
    )

    return " ".join(
        value.split()
    )


def normalize_club(value):
    value = ascii_text(value)

    aliases = {
        "internazionale": "inter",
        "inter milan": "inter",
        "fc internazionale": "inter",
        "ac milan": "milan",
        "juventus fc": "juventus",
        "as roma": "roma",
        "ss lazio": "lazio",
        "ssc napoli": "napoli",
        "atalanta bc": "atalanta",
        "acf fiorentina": "fiorentina",
        "us lecce": "lecce",
        "parma calcio 1913": "parma",
        "us sassuolo": "sassuolo",
        "us sassuolo calcio": "sassuolo",
        "torino fc": "torino",
        "udinese calcio": "udinese",
        "bologna fc 1909": "bologna",
        "cagliari calcio": "cagliari",
        "como 1907": "como",
        "genoa cfc": "genoa",
        "venezia fc": "venezia",
        "frosinone calcio": "frosinone",
        "ac monza": "monza",
    }

    normalized = re.sub(
        r"[^a-z0-9]+",
        " ",
        value,
    ).strip()

    return aliases.get(
        normalized,
        normalized,
    )


def normalize_height(value):
    value = (value or "").strip().lower()

    if not value:
        return None

    # 191cm
    cm_match = re.search(
        r"(\d{3})\s*cm",
        value,
    )

    if cm_match:
        return int(
            cm_match.group(1)
        )

    # 1,91 m / 1.91 m
    meter_match = re.search(
        r"([12])[\.,](\d{1,2})\s*m",
        value,
    )

    if meter_match:
        meters = int(
            meter_match.group(1)
        )

        decimals = (
            meter_match.group(2)
            .ljust(2, "0")[:2]
        )

        return (
            meters * 100
            + int(decimals)
        )

    numbers = re.findall(
        r"\d+",
        value,
    )

    if len(numbers) == 1:
        number = int(numbers[0])

        if 140 <= number <= 220:
            return number

    return None


def normalize_dob(value):
    value = (value or "").strip()

    if re.fullmatch(
        r"\d{4}-\d{2}-\d{2}",
        value,
    ):
        return value

    return value


def nationality_tokens(value):
    value = ascii_text(value)

    value = value.replace(
        "/",
        ",",
    )

    value = value.replace(
        ";",
        ",",
    )

    parts = []

    for item in value.split(","):
        item = re.sub(
            r"[^a-z0-9 ]+",
            " ",
            item,
        )

        item = " ".join(
            item.split()
        )

        if item:
            parts.append(item)

    return set(parts)


def name_tokens(value):
    return set(
        normalize_name(value).split()
    )


def surname_token(value):
    tokens = normalize_name(
        value
    ).split()

    if not tokens:
        return ""

    return tokens[-1]


def same_height(fc, tm):
    fc_height = normalize_height(fc)
    tm_height = normalize_height(tm)

    if (
        fc_height is None
        or tm_height is None
    ):
        return False

    # Piccola tolleranza di 1 cm:
    # le fonti possono arrotondare.
    return (
        abs(fc_height - tm_height)
        <= 1
    )


def nationality_overlap(fc, tm):
    fc_tokens = nationality_tokens(fc)
    tm_tokens = nationality_tokens(tm)

    if not fc_tokens or not tm_tokens:
        return False

    return bool(
        fc_tokens.intersection(
            tm_tokens
        )
    )


def extract_tm(row):
    return {
        "id": first_value(
            row,
            "transfermarkt_player_id",
            "player_id",
            "external_id",
        ),
        "name": first_value(
            row,
            "full_name",
            "name",
            "player_name",
        ),
        "club": first_value(
            row,
            "team",
            "club",
            "team_name",
            "transfermarkt_team_name",
        ),
        "dob": first_value(
            row,
            "date_of_birth",
            "birth_date",
            "dob",
        ),
        "height": first_value(
            row,
            "height",
            "height_cm",
        ),
        "nationality": first_value(
            row,
            "nationality",
            "nationality_name",
            "country",
        ),
        "url": first_value(
            row,
            "player_url",
            "url",
        ),
        "raw": row,
    }


def extract_fc(row):
    return {
        "id": first_value(
            row,
            "fantacalcio_player_id",
        ),
        "short_name": first_value(
            row,
            "short_name",
        ),
        "name": first_value(
            row,
            "full_name",
        ),
        "club": first_value(
            row,
            "club",
        ),
        "role": first_value(
            row,
            "fantasy_role",
        ),
        "dob": first_value(
            row,
            "date_of_birth",
        ),
        "height": first_value(
            row,
            "height",
        ),
        "nationality": first_value(
            row,
            "nationality",
        ),
        "foot": first_value(
            row,
            "preferred_foot",
        ),
        "url": first_value(
            row,
            "player_url",
        ),
        "raw": row,
    }


def make_result(
    fc,
    tm=None,
    status="UNMATCHED",
    rule="",
    candidate_count=0,
    notes="",
):
    tm = tm or {
        "id": "",
        "name": "",
        "club": "",
        "dob": "",
        "height": "",
        "nationality": "",
        "url": "",
    }

    fc_name_norm = normalize_name(
        fc["name"]
    )

    tm_name_norm = normalize_name(
        tm["name"]
    )

    name_exact = bool(
        fc_name_norm
        and tm_name_norm
        and fc_name_norm == tm_name_norm
    )

    dob_match = bool(
        fc["dob"]
        and tm["dob"]
        and normalize_dob(fc["dob"])
        == normalize_dob(tm["dob"])
    )

    club_match = bool(
        fc["club"]
        and tm["club"]
        and normalize_club(fc["club"])
        == normalize_club(tm["club"])
    )

    height_match = same_height(
        fc["height"],
        tm["height"],
    )

    nat_overlap = nationality_overlap(
        fc["nationality"],
        tm["nationality"],
    )

    return {
        "status": status,
        "match_rule": rule,

        "fantacalcio_player_id":
            fc["id"],

        "fantacalcio_short_name":
            fc["short_name"],

        "fantacalcio_full_name":
            fc["name"],

        "fantacalcio_club":
            fc["club"],

        "fantasy_role":
            fc["role"],

        "fantacalcio_dob":
            fc["dob"],

        "fantacalcio_height":
            fc["height"],

        "fantacalcio_nationality":
            fc["nationality"],

        "fantacalcio_foot":
            fc["foot"],

        "fantacalcio_url":
            fc["url"],

        "transfermarkt_player_id":
            tm["id"],

        "transfermarkt_full_name":
            tm["name"],

        "transfermarkt_team":
            tm["club"],

        "transfermarkt_dob":
            tm["dob"],

        "transfermarkt_height":
            tm["height"],

        "transfermarkt_nationality":
            tm["nationality"],

        "transfermarkt_url":
            tm["url"],

        "name_exact":
            "YES" if name_exact else "NO",

        "dob_match":
            "YES" if dob_match else "NO",

        "club_match":
            "YES" if club_match else "NO",

        "height_match":
            "YES" if height_match else "NO",

        "nationality_overlap":
            "YES" if nat_overlap else "NO",

        "candidate_count":
            candidate_count,

        "notes":
            notes,
    }


def deterministic_candidates(
    fc,
    tm_rows,
):
    fc_club = normalize_club(
        fc["club"]
    )

    # Prima restringiamo SEMPRE al club corrente.
    club_candidates = [
        tm
        for tm in tm_rows
        if (
            fc_club
            and normalize_club(tm["club"])
            == fc_club
        )
    ]

    fc_name = normalize_name(
        fc["name"]
    )

    fc_dob = normalize_dob(
        fc["dob"]
    )

    # ==================================================
    # RULE 1:
    # exact full name + same club
    # ==================================================

    exact_name = [
        tm
        for tm in club_candidates
        if normalize_name(tm["name"])
        == fc_name
    ]

    if len(exact_name) == 1:
        tm = exact_name[0]

        if (
            fc_dob
            and tm["dob"]
            and normalize_dob(tm["dob"])
            != fc_dob
        ):
            return (
                "REVIEW",
                "EXACT_NAME_CLUB_DOB_CONFLICT",
                tm,
                len(exact_name),
                "Nome e club coincidono ma DOB differente.",
            )

        if (
            fc_dob
            and tm["dob"]
            and normalize_dob(tm["dob"])
            == fc_dob
        ):
            return (
                "HIGH",
                "EXACT_NAME_DOB_CLUB",
                tm,
                1,
                "",
            )

        return (
            "REVIEW",
            "EXACT_NAME_CLUB_DOB_MISSING",
            tm,
            1,
            "DOB insufficiente.",
        )

    if len(exact_name) > 1:
        exact_name_dob = [
            tm
            for tm in exact_name
            if (
                fc_dob
                and normalize_dob(tm["dob"])
                == fc_dob
            )
        ]

        if len(exact_name_dob) == 1:
            return (
                "HIGH",
                "EXACT_NAME_DOB_CLUB_UNIQUE",
                exact_name_dob[0],
                len(exact_name),
                "",
            )

        return (
            "REVIEW",
            "MULTIPLE_EXACT_NAME_CLUB",
            None,
            len(exact_name),
            "Più candidati con stesso nome nel club.",
        )

    # ==================================================
    # RULE 2:
    # DOB + club = base forte.
    # Serve UN candidato unico e almeno
    # altezza oppure nazionalità coerente.
    #
    # Utile per Ederson / mononimi / nomi abbreviati.
    # ==================================================

    dob_club = [
        tm
        for tm in club_candidates
        if (
            fc_dob
            and tm["dob"]
            and normalize_dob(tm["dob"])
            == fc_dob
        )
    ]

    if len(dob_club) == 1:
        tm = dob_club[0]

        height_ok = same_height(
            fc["height"],
            tm["height"],
        )

        nationality_ok = (
            nationality_overlap(
                fc["nationality"],
                tm["nationality"],
            )
        )

        if height_ok or nationality_ok:
            return (
                "ALIAS_HIGH",
                "DOB_CLUB_UNIQUE_PLUS_CORROBORATION",
                tm,
                1,
                (
                    "Nome differente; identità deterministica "
                    "tramite DOB+club+candidato unico+"
                    "altezza/nazionalità."
                ),
            )

        return (
            "REVIEW",
            "DOB_CLUB_UNIQUE_WEAK_CORROBORATION",
            tm,
            1,
            (
                "DOB e club coincidono ma manca "
                "corroborazione sufficiente."
            ),
        )

    if len(dob_club) > 1:
        return (
            "REVIEW",
            "MULTIPLE_DOB_CLUB",
            None,
            len(dob_club),
            "Più candidati con stessa DOB nello stesso club.",
        )

    # ==================================================
    # RULE 3:
    # nome con token sovrapposti + club +
    # altezza/nazionalità.
    #
    # MA non diventa HIGH senza DOB.
    # Serve solo a individuare REVIEW utili.
    # ==================================================

    fc_tokens = name_tokens(
        fc["name"]
    )

    possible = []

    for tm in club_candidates:
        tm_tokens = name_tokens(
            tm["name"]
        )

        common = (
            fc_tokens
            .intersection(tm_tokens)
        )

        if not common:
            continue

        corroboration = 0

        if same_height(
            fc["height"],
            tm["height"],
        ):
            corroboration += 1

        if nationality_overlap(
            fc["nationality"],
            tm["nationality"],
        ):
            corroboration += 1

        if corroboration:
            possible.append(tm)

    if len(possible) == 1:
        return (
            "REVIEW",
            "NAME_TOKEN_CLUB_CORROBORATION_NO_DOB",
            possible[0],
            1,
            (
                "Candidato plausibile ma DOB non coincide/non disponibile. "
                "Richiede verifica umana o terza fonte."
            ),
        )

    if len(possible) > 1:
        return (
            "REVIEW",
            "MULTIPLE_WEAK_CANDIDATES",
            None,
            len(possible),
            "Più candidati plausibili.",
        )

    return (
        "UNMATCHED",
        "NO_DETERMINISTIC_CANDIDATE",
        None,
        0,
        "",
    )


def catalog_key(row):
    fc_id = first_value(
        row,
        "fantacalcio_player_id",
        "source_player_id",
        "external_id",
    )

    if fc_id:
        return (
            "ID",
            fc_id,
        )

    name = first_value(
        row,
        "full_name",
        "player_name",
        "name",
        "canonical_name",
    )

    club = first_value(
        row,
        "club",
        "team",
        "club_name",
    )

    if name:
        return (
            "NAME_CLUB",
            normalize_name(name),
            normalize_club(club),
        )

    return None


def compare_catalog(
    fc_rows,
    catalog_rows,
):
    fc_id_set = {
        row["fantacalcio_player_id"]
        for row in fc_rows
        if row["fantacalcio_player_id"]
    }

    catalog_ids = set()

    for row in catalog_rows:
        value = first_value(
            row,
            "fantacalcio_player_id",
        )

        if value:
            catalog_ids.add(value)

    # Caso migliore: il catalog export contiene ID Fantacalcio.
    if catalog_ids:
        missing = [
            row
            for row in fc_rows
            if (
                row["fantacalcio_player_id"]
                not in catalog_ids
            )
        ]

        extra = [
            row
            for row in catalog_rows
            if (
                first_value(
                    row,
                    "fantacalcio_player_id",
                )
                not in fc_id_set
            )
        ]

        return missing, extra, "FANTACALCIO_ID"

    # Fallback: nome + club.
    fc_by_name_club = {
        (
            normalize_name(
                row["full_name"]
            ),
            normalize_club(
                row["club"]
            ),
        )
        for row in fc_rows
    }

    catalog_name_club = set()

    for row in catalog_rows:
        name = first_value(
            row,
            "full_name",
            "player_name",
            "name",
            "canonical_name",
            "catalog_name",
        )

        club = first_value(
            row,
            "club",
            "team",
            "club_name",
            "current_club",
            "catalog_club",
        )

        if name:
            catalog_name_club.add(
                (
                    normalize_name(name),
                    normalize_club(club),
                )
            )

    if not catalog_name_club:
        return (
            [],
            [],
            "CATALOG_SCHEMA_NOT_RECOGNIZED",
        )

    missing = [
        row
        for row in fc_rows
        if (
            normalize_name(
                row["full_name"]
            ),
            normalize_club(
                row["club"]
            ),
        )
        not in catalog_name_club
    ]

    extra = []

    for row in catalog_rows:
        name = first_value(
            row,
            "full_name",
            "player_name",
            "name",
            "canonical_name",
            "catalog_name",
        )

        club = first_value(
            row,
            "club",
            "team",
            "club_name",
            "current_club",
            "catalog_club",
        )

        if not name:
            continue

        if (
            normalize_name(name),
            normalize_club(club),
        ) not in fc_by_name_club:
            extra.append(row)

    return (
        missing,
        extra,
        "NAME_CLUB_FALLBACK",
    )


def main():
    print(
        "Carico master Fantacalcio..."
    )

    fc_raw = read_csv(
        FC_FILE
    )

    print(
        f"Fantacalcio: {len(fc_raw)}"
    )

    print(
        "Carico master Transfermarkt..."
    )

    tm_raw = read_csv(
        TM_FILE
    )

    print(
        f"Transfermarkt: {len(tm_raw)}"
    )

    fc_rows = [
        extract_fc(row)
        for row in fc_raw
    ]

    tm_rows = [
        extract_tm(row)
        for row in tm_raw
    ]

    # Protezione base.
    fc_ids = [
        row["id"]
        for row in fc_rows
    ]

    tm_ids = [
        row["id"]
        for row in tm_rows
    ]

    if len(fc_ids) != len(set(fc_ids)):
        raise RuntimeError(
            "Duplicati negli ID Fantacalcio."
        )

    if (
        all(tm_ids)
        and len(tm_ids) != len(set(tm_ids))
    ):
        raise RuntimeError(
            "Duplicati negli ID Transfermarkt."
        )

    results = []

    for index, fc in enumerate(
        fc_rows,
        start=1,
    ):
        (
            status,
            rule,
            tm,
            candidate_count,
            notes,
        ) = deterministic_candidates(
            fc,
            tm_rows,
        )

        results.append(
            make_result(
                fc=fc,
                tm=tm,
                status=status,
                rule=rule,
                candidate_count=candidate_count,
                notes=notes,
            )
        )

    counts = Counter(
        row["status"]
        for row in results
    )

    high_rows = [
        row
        for row in results
        if row["status"] == "HIGH"
    ]

    alias_high_rows = [
        row
        for row in results
        if row["status"] == "ALIAS_HIGH"
    ]

    review_rows = [
        row
        for row in results
        if row["status"] == "REVIEW"
    ]

    unmatched_rows = [
        row
        for row in results
        if row["status"] == "UNMATCHED"
    ]

    dob_conflicts = [
        row
        for row in results
        if "DOB_CONFLICT"
        in row["match_rule"]
    ]

    OUTPUT_DIR.mkdir(
        parents=True,
        exist_ok=True,
    )

    write_csv(
        ALL_MATCHES_FILE,
        results,
        OUTPUT_FIELDS,
    )

    write_csv(
        HIGH_FILE,
        high_rows,
        OUTPUT_FIELDS,
    )

    write_csv(
        ALIAS_HIGH_FILE,
        alias_high_rows,
        OUTPUT_FIELDS,
    )

    write_csv(
        REVIEW_FILE,
        review_rows,
        OUTPUT_FIELDS,
    )

    write_csv(
        UNMATCHED_FILE,
        unmatched_rows,
        OUTPUT_FIELDS,
    )

    write_csv(
        DOB_CONFLICT_FILE,
        dob_conflicts,
        OUTPUT_FIELDS,
    )

    catalog_missing = []
    catalog_extra = []
    catalog_mode = "CATALOG_FILE_NOT_FOUND"

    if CATALOG_FILE.exists():
        catalog_rows = read_csv(
            CATALOG_FILE
        )

        (
            catalog_missing,
            catalog_extra,
            catalog_mode,
        ) = compare_catalog(
            fc_raw,
            catalog_rows,
        )

        if catalog_missing:
            write_csv(
                CATALOG_MISSING_FILE,
                catalog_missing,
                list(catalog_missing[0].keys()),
            )
        else:
            write_csv(
                CATALOG_MISSING_FILE,
                [],
                list(fc_raw[0].keys()),
            )

        if catalog_extra:
            write_csv(
                CATALOG_EXTRA_FILE,
                catalog_extra,
                list(catalog_extra[0].keys()),
            )
        else:
            fields = (
                list(catalog_rows[0].keys())
                if catalog_rows
                else ["empty"]
            )

            write_csv(
                CATALOG_EXTRA_FILE,
                [],
                fields,
            )

    # ----------------------------------------------
    # Audit collisioni TM:
    # lo stesso Transfermarkt ID non può essere
    # assegnato HIGH a due giocatori Fantacalcio.
    # ----------------------------------------------

    accepted = (
        high_rows
        + alias_high_rows
    )

    accepted_tm_ids = [
        row["transfermarkt_player_id"]
        for row in accepted
        if row["transfermarkt_player_id"]
    ]

    collision_ids = sorted(
        tm_id
        for tm_id, count
        in Counter(
            accepted_tm_ids
        ).items()
        if count > 1
    )

    report = []

    report.append(
        "FANTACALCIO <-> TRANSFERMARKT IDENTITY AUDIT"
    )

    report.append(
        "Season: 2026/2027"
    )

    report.append("")

    report.append(
        f"Fantacalcio rows: {len(fc_rows)}"
    )

    report.append(
        f"Transfermarkt rows: {len(tm_rows)}"
    )

    report.append("")

    report.append(
        f"HIGH exact/deterministic: {len(high_rows)}"
    )

    report.append(
        f"ALIAS_HIGH: {len(alias_high_rows)}"
    )

    report.append(
        f"TOTAL SAFE HIGH: "
        f"{len(high_rows) + len(alias_high_rows)}"
    )

    report.append(
        f"REVIEW: {len(review_rows)}"
    )

    report.append(
        f"UNMATCHED: {len(unmatched_rows)}"
    )

    report.append(
        f"DOB CONFLICT: {len(dob_conflicts)}"
    )

    report.append(
        f"Transfermarkt ID collisions among HIGH: "
        f"{len(collision_ids)}"
    )

    report.append("")

    report.append(
        f"Operational catalog audit mode: "
        f"{catalog_mode}"
    )

    report.append(
        f"Fantacalcio players missing from operational catalog: "
        f"{len(catalog_missing)}"
    )

    report.append(
        f"Operational catalog rows absent from current Fantacalcio: "
        f"{len(catalog_extra)}"
    )

    if collision_ids:
        report.append("")
        report.append(
            "HIGH COLLISIONS"
        )

        for tm_id in collision_ids:
            report.append(
                f"- Transfermarkt ID {tm_id}"
            )

    REPORT_FILE.write_text(
        "\n".join(report) + "\n",
        encoding="utf-8",
    )

    print()
    print(
        "======================================================================"
    )

    print(
        "AUDIT FINALE IDENTITY FANTACALCIO <-> TRANSFERMARKT"
    )

    print(
        "======================================================================"
    )

    print(
        f"Fantacalcio:                    {len(fc_rows)}"
    )

    print(
        f"Transfermarkt:                  {len(tm_rows)}"
    )

    print()

    print(
        f"HIGH:                           {len(high_rows)}"
    )

    print(
        f"ALIAS HIGH:                     {len(alias_high_rows)}"
    )

    print(
        f"TOTAL SAFE HIGH:                "
        f"{len(high_rows) + len(alias_high_rows)}"
    )

    print(
        f"REVIEW:                         {len(review_rows)}"
    )

    print(
        f"UNMATCHED:                      {len(unmatched_rows)}"
    )

    print(
        f"DOB CONFLICT:                   {len(dob_conflicts)}"
    )

    print(
        f"HIGH TM-ID COLLISIONS:          {len(collision_ids)}"
    )

    print()

    print(
        f"CATALOG AUDIT MODE:             {catalog_mode}"
    )

    print(
        f"FANTACALCIO -> CATALOG MISSING: {len(catalog_missing)}"
    )

    print(
        f"CATALOG -> FANTACALCIO EXTRA:   {len(catalog_extra)}"
    )

    print()

    print(
        f"ALL MATCHES: {ALL_MATCHES_FILE}"
    )

    print(
        f"HIGH:        {HIGH_FILE}"
    )

    print(
        f"ALIAS HIGH:  {ALIAS_HIGH_FILE}"
    )

    print(
        f"REVIEW:      {REVIEW_FILE}"
    )

    print(
        f"UNMATCHED:   {UNMATCHED_FILE}"
    )

    print(
        f"DOB:         {DOB_CONFLICT_FILE}"
    )

    print(
        f"REPORT:      {REPORT_FILE}"
    )


if __name__ == "__main__":
    try:
        main()

    except Exception as exc:
        print()
        print(
            f"ERRORE FATALE: {exc}"
        )
        sys.exit(1)
