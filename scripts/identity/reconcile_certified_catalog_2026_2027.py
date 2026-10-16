import csv
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

CERTIFIED_FILE = (
    ROOT
    / "data"
    / "identity"
    / "comparison-2026-2027"
    / "fantacalcio-transfermarkt-certified-final.csv"
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

ALL_FILE = OUTPUT_DIR / "catalog-reconciliation-final.csv"
SAME_FILE = OUTPUT_DIR / "catalog-reconciliation-same-player.csv"
TRANSFER_FILE = OUTPUT_DIR / "catalog-reconciliation-transfers.csv"
NEW_FILE = OUTPUT_DIR / "catalog-reconciliation-new-players.csv"
OUTGOING_FILE = OUTPUT_DIR / "catalog-reconciliation-outgoing.csv"
REVIEW_FILE = OUTPUT_DIR / "catalog-reconciliation-review.csv"
REPORT_FILE = OUTPUT_DIR / "catalog-reconciliation-final-report.txt"


def read_csv(path):
    if not path.exists():
        raise RuntimeError(f"File non trovato: {path}")

    with path.open("r", encoding="utf-8-sig", newline="") as f:
        return list(csv.DictReader(f))


def write_csv(path, rows, fields):
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=fields,
            extrasaction="ignore",
        )
        writer.writeheader()
        writer.writerows(rows)


def norm(value):
    value = (value or "").strip().lower()

    value = unicodedata.normalize("NFKD", value)
    value = "".join(
        c
        for c in value
        if not unicodedata.combining(c)
    )

    value = re.sub(r"[^a-z0-9]+", " ", value)
    return " ".join(value.split())


def club_norm(value):
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
        "us sassuolo calcio": "sassuolo",
        "us sassuolo": "sassuolo",
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

    value = norm(value)
    return aliases.get(value, value)


def first(row, *fields):
    for field in fields:
        value = (row.get(field) or "").strip()
        if value:
            return value
    return ""


def fc_short_base(value):
    """
    Esempi:
    Maldini       -> maldini
    Chalobah T.   -> chalobah
    Pellegrini Lo.-> pellegrini
    Tourè E.      -> toure
    """
    value = norm(value)

    if not value:
        return ""

    return value.split()[0]


def catalog_base(value):
    value = norm(value)

    if not value:
        return ""

    return value.split()[0]


def make_result(
    fc,
    catalog=None,
    category="NEW_PLAYER",
    rule="",
    notes="",
):
    catalog = catalog or {}

    return {
        "category": category,
        "rule": rule,

        "fantacalcio_player_id":
            first(fc, "fantacalcio_player_id"),

        "fantacalcio_short_name":
            first(fc, "fantacalcio_short_name"),

        "fantacalcio_full_name":
            first(fc, "fantacalcio_full_name"),

        "fantacalcio_club":
            first(fc, "fantacalcio_club"),

        "fantasy_role":
            first(fc, "fantasy_role"),

        "canonical_date_of_birth":
            first(fc, "canonical_date_of_birth"),

        "transfermarkt_player_id":
            first(fc, "transfermarkt_player_id"),

        "season_status":
            first(fc, "season_status"),

        "catalog_visibility":
            first(fc, "catalog_visibility"),

        "ai_eligible":
            first(fc, "ai_eligible"),

        "existing_player_id":
            first(catalog, "player_id"),

        "existing_catalog_name":
            first(catalog, "catalog_name"),

        "existing_catalog_club":
            first(catalog, "catalog_club"),

        "existing_fantasy_role":
            first(catalog, "fantasy_role"),

        "existing_transfermarkt_player_id":
            first(catalog, "transfermarkt_player_id"),

        "existing_date_of_birth":
            first(catalog, "date_of_birth"),

        "notes": notes,
    }


def main():

    certified = read_csv(CERTIFIED_FILE)
    catalog = read_csv(CATALOG_FILE)

    visible = [
        row
        for row in certified
        if row.get("catalog_visibility") == "VISIBLE"
    ]

    hidden = [
        row
        for row in certified
        if row.get("catalog_visibility") == "HIDDEN"
    ]

    if len(certified) != 497:
        raise RuntimeError(
            f"Attesi 497 certified record, trovati {len(certified)}"
        )

    if len(visible) != 495:
        raise RuntimeError(
            f"Attesi 495 visibili, trovati {len(visible)}"
        )

    if len(hidden) != 2:
        raise RuntimeError(
            f"Attesi 2 nascosti, trovati {len(hidden)}"
        )

    if len(catalog) != 492:
        raise RuntimeError(
            f"Attesi 492 catalog record, trovati {len(catalog)}"
        )


    # ==========================================================
    # INDICI CATALOGO
    # ==========================================================

    tm_index = defaultdict(list)
    dob_name_index = defaultdict(list)
    short_club_index = defaultdict(list)
    short_index = defaultdict(list)

    for row in catalog:

        tm_id = first(
            row,
            "transfermarkt_player_id",
        )

        if tm_id:
            tm_index[tm_id].append(row)

        dob = first(
            row,
            "date_of_birth",
        )

        base_name = catalog_base(
            first(row, "catalog_name")
        )

        club = club_norm(
            first(row, "catalog_club")
        )

        if dob and base_name:
            dob_name_index[
                (dob, base_name)
            ].append(row)

        if base_name and club:
            short_club_index[
                (base_name, club)
            ].append(row)

        if base_name:
            short_index[
                base_name
            ].append(row)


    used_player_ids = set()
    results = []


    # ==========================================================
    # RECONCILIATION VISIBILI
    # ==========================================================

    for fc in visible:

        fc_tm = first(
            fc,
            "transfermarkt_player_id",
        )

        fc_dob = first(
            fc,
            "canonical_date_of_birth",
        )

        fc_short = fc_short_base(
            first(fc, "fantacalcio_short_name")
        )

        fc_club = club_norm(
            first(fc, "fantacalcio_club")
        )

        candidate = None
        category = None
        rule = None
        notes = ""


        # ------------------------------------------------------
        # RULE 1
        # Transfermarkt ID univoco = identità fortissima.
        # ------------------------------------------------------

        if fc_tm:
            matches = tm_index.get(
                fc_tm,
                [],
            )

            if len(matches) == 1:
                candidate = matches[0]

                old_club = club_norm(
                    first(
                        candidate,
                        "catalog_club",
                    )
                )

                if old_club == fc_club:
                    category = "SAME_PLAYER"
                    rule = "TRANSFERMARKT_ID_SAME_CLUB"
                else:
                    category = "SAME_PLAYER_TRANSFER"
                    rule = "TRANSFERMARKT_ID_CLUB_CHANGED"

                    notes = (
                        f"Club precedente={first(candidate,'catalog_club')} "
                        f"-> club attuale={first(fc,'fantacalcio_club')}"
                    )

            elif len(matches) > 1:
                category = "REVIEW"
                rule = "DUPLICATE_TRANSFERMARKT_ID"

                notes = (
                    f"TM ID {fc_tm} presente in "
                    f"{len(matches)} righe catalogo."
                )


        # ------------------------------------------------------
        # RULE 2
        # DOB + short/base name univoci.
        # Utile per vecchie righe senza TM ID.
        # ------------------------------------------------------

        if category is None and fc_dob and fc_short:

            matches = dob_name_index.get(
                (fc_dob, fc_short),
                [],
            )

            if len(matches) == 1:
                candidate = matches[0]

                old_club = club_norm(
                    first(
                        candidate,
                        "catalog_club",
                    )
                )

                if old_club == fc_club:
                    category = "SAME_PLAYER"
                    rule = "DOB_SHORT_NAME_SAME_CLUB"
                else:
                    category = "SAME_PLAYER_TRANSFER"
                    rule = "DOB_SHORT_NAME_CLUB_CHANGED"

                    notes = (
                        f"Identità recuperata senza TM-ID precedente. "
                        f"Club {first(candidate,'catalog_club')} "
                        f"-> {first(fc,'fantacalcio_club')}."
                    )

            elif len(matches) > 1:
                category = "REVIEW"
                rule = "MULTIPLE_DOB_SHORT_NAME"


        # ------------------------------------------------------
        # RULE 3
        # short name + stesso club.
        #
        # Non usiamo questa regola per trasferimenti:
        # surname-only non deve diventare HIGH cross-club.
        # ------------------------------------------------------

        if category is None and fc_short and fc_club:

            matches = short_club_index.get(
                (fc_short, fc_club),
                [],
            )

            if len(matches) == 1:

                possible = matches[0]

                old_dob = first(
                    possible,
                    "date_of_birth",
                )

                # Se entrambe le DOB esistono e confliggono,
                # niente auto-match.
                if (
                    fc_dob
                    and old_dob
                    and fc_dob != old_dob
                ):
                    category = "REVIEW"
                    rule = "SHORT_CLUB_DOB_CONFLICT"

                    notes = (
                        f"FC DOB={fc_dob}; "
                        f"catalog DOB={old_dob}"
                    )

                else:
                    candidate = possible
                    category = "SAME_PLAYER"
                    rule = "SHORT_NAME_CLUB_UNIQUE"


            elif len(matches) > 1:
                category = "REVIEW"
                rule = "MULTIPLE_SHORT_NAME_CLUB"


        # ------------------------------------------------------
        # RULE 4
        # Nome corto unico ma club diverso:
        # NON auto-match.
        #
        # Lo segnaliamo REVIEW perché surname-only cross-club
        # non è prova sufficiente.
        # ------------------------------------------------------

        if category is None and fc_short:

            matches = short_index.get(
                fc_short,
                [],
            )

            if len(matches) == 1:

                possible = matches[0]

                old_club = club_norm(
                    first(
                        possible,
                        "catalog_club",
                    )
                )

                old_dob = first(
                    possible,
                    "date_of_birth",
                )

                if (
                    old_club != fc_club
                    and
                    (
                        not fc_dob
                        or not old_dob
                        or fc_dob != old_dob
                    )
                ):
                    category = "REVIEW"
                    rule = "SURNAME_ONLY_CROSS_CLUB_NOT_SUFFICIENT"

                    notes = (
                        f"Possibile player_id "
                        f"{first(possible,'player_id')} "
                        f"ma identità non certificabile "
                        f"solo dal cognome."
                    )


        # ------------------------------------------------------
        # Nessun player precedente affidabile.
        # ------------------------------------------------------

        if category is None:
            category = "NEW_PLAYER"
            rule = "NO_EXISTING_CERTIFIED_PLAYER"


        # ------------------------------------------------------
        # Collision protection:
        # uno stesso player_id esistente non può essere
        # assegnato a due giocatori correnti.
        # ------------------------------------------------------

        if candidate:

            existing_id = first(
                candidate,
                "player_id",
            )

            if existing_id in used_player_ids:
                category = "REVIEW"
                rule = "EXISTING_PLAYER_ID_COLLISION"

                notes = (
                    f"player_id {existing_id} già assegnato "
                    f"a un altro record corrente."
                )

            else:
                used_player_ids.add(
                    existing_id
                )


        results.append(
            make_result(
                fc,
                candidate,
                category,
                rule,
                notes,
            )
        )


    # ==========================================================
    # CATALOGO PRECEDENTE NON PIÙ RICONCILIATO
    # ==========================================================

    outgoing = []

    for row in catalog:

        player_id = first(
            row,
            "player_id",
        )

        if player_id not in used_player_ids:

            outgoing.append({
                "player_id":
                    player_id,

                "catalog_name":
                    first(row, "catalog_name"),

                "catalog_club":
                    first(row, "catalog_club"),

                "fantasy_role":
                    first(row, "fantasy_role"),

                "transfermarkt_player_id":
                    first(
                        row,
                        "transfermarkt_player_id",
                    ),

                "date_of_birth":
                    first(
                        row,
                        "date_of_birth",
                    ),

                "previous_match_status":
                    first(
                        row,
                        "match_status",
                    ),

                "previous_confidence":
                    first(
                        row,
                        "confidence",
                    ),

                "recommended_action":
                    "PRESERVE_PLAYER_AND_DEACTIVATE_REGISTRATION",

                "notes":
                    "Assente dal catalogo corrente riconciliato. Non eliminare il player.",
            })


    # ==========================================================
    # OUTPUT
    # ==========================================================

    fields = [
        "category",
        "rule",
        "fantacalcio_player_id",
        "fantacalcio_short_name",
        "fantacalcio_full_name",
        "fantacalcio_club",
        "fantasy_role",
        "canonical_date_of_birth",
        "transfermarkt_player_id",
        "season_status",
        "catalog_visibility",
        "ai_eligible",
        "existing_player_id",
        "existing_catalog_name",
        "existing_catalog_club",
        "existing_fantasy_role",
        "existing_transfermarkt_player_id",
        "existing_date_of_birth",
        "notes",
    ]

    write_csv(
        ALL_FILE,
        results,
        fields,
    )

    same = [
        r
        for r in results
        if r["category"] == "SAME_PLAYER"
    ]

    transfers = [
        r
        for r in results
        if r["category"] == "SAME_PLAYER_TRANSFER"
    ]

    new_players = [
        r
        for r in results
        if r["category"] == "NEW_PLAYER"
    ]

    review = [
        r
        for r in results
        if r["category"] == "REVIEW"
    ]

    write_csv(
        SAME_FILE,
        same,
        fields,
    )

    write_csv(
        TRANSFER_FILE,
        transfers,
        fields,
    )

    write_csv(
        NEW_FILE,
        new_players,
        fields,
    )

    write_csv(
        REVIEW_FILE,
        review,
        fields,
    )

    outgoing_fields = [
        "player_id",
        "catalog_name",
        "catalog_club",
        "fantasy_role",
        "transfermarkt_player_id",
        "date_of_birth",
        "previous_match_status",
        "previous_confidence",
        "recommended_action",
        "notes",
    ]

    write_csv(
        OUTGOING_FILE,
        outgoing,
        outgoing_fields,
    )


    # ==========================================================
    # AUDIT
    # ==========================================================

    existing_ids = [
        r["existing_player_id"]
        for r in results
        if r["existing_player_id"]
    ]

    collisions = [
        player_id
        for player_id, count
        in Counter(existing_ids).items()
        if count > 1
    ]

    reconciled_existing = (
        len(same)
        + len(transfers)
    )

    arithmetic_ok = (
        len(same)
        + len(transfers)
        + len(new_players)
        + len(review)
        == len(visible)
    )

    gate = "PASS"

    if review:
        gate = "REVIEW_REQUIRED"

    if collisions:
        gate = "FAIL_COLLISION"

    if not arithmetic_ok:
        gate = "FAIL_ARITHMETIC"


    report = f"""
FANTACALCIO INTELLIGENCE
FINAL CATALOG RECONCILIATION
Season 2026/2027

CERTIFIED TOTAL:              {len(certified)}
VISIBLE CURRENT:              {len(visible)}
HIDDEN CURRENT:               {len(hidden)}

OLD OPERATIONAL CATALOG:      {len(catalog)}

SAME PLAYER:                  {len(same)}
SAME PLAYER / TRANSFER:       {len(transfers)}
NEW PLAYER:                   {len(new_players)}
REVIEW:                       {len(review)}

RECONCILED EXISTING:          {reconciled_existing}
OLD CATALOG OUTGOING:         {len(outgoing)}

EXISTING PLAYER COLLISIONS:   {len(collisions)}
ARITHMETIC CHECK:             {"PASS" if arithmetic_ok else "FAIL"}

RECONCILIATION GATE:          {gate}

Policy:
- No player is deleted.
- Transfers reuse existing player_id.
- Outgoing players preserve identity/history.
- Outgoing registrations become inactive/hidden.
- New players are created only when no safe existing identity exists.
- Surname-only cross-club is never sufficient for automatic identity.
"""

    REPORT_FILE.write_text(
        report.strip() + "\n",
        encoding="utf-8",
    )


    # ==========================================================
    # TERMINALE
    # ==========================================================

    print()
    print("==============================================================")
    print("FINAL CATALOG RECONCILIATION 2026/27")
    print("==============================================================")

    print(f"Certified totale:                {len(certified)}")
    print(f"Visibili correnti:               {len(visible)}")
    print(f"Nascosti correnti:               {len(hidden)}")
    print(f"Catalogo operativo precedente:   {len(catalog)}")

    print()
    print(f"SAME PLAYER:                     {len(same)}")
    print(f"SAME PLAYER / TRANSFER:          {len(transfers)}")
    print(f"NEW PLAYER:                      {len(new_players)}")
    print(f"REVIEW:                          {len(review)}")
    print(f"OLD CATALOG OUTGOING:            {len(outgoing)}")

    print()
    print(f"PLAYER-ID COLLISIONS:            {len(collisions)}")
    print(
        f"ARITHMETIC CHECK:                "
        f"{'PASS' if arithmetic_ok else 'FAIL'}"
    )
    print(f"RECONCILIATION GATE:             {gate}")


    print()
    print("================ TRANSFER ================")

    for r in transfers:
        print(
            f"{r['existing_player_id']} | "
            f"{r['fantacalcio_full_name']} | "
            f"{r['existing_catalog_club']} -> "
            f"{r['fantacalcio_club']} | "
            f"{r['rule']}"
        )


    print()
    print("================ NEW PLAYER ================")

    for r in new_players:
        print(
            f"FC {r['fantacalcio_player_id']} | "
            f"{r['fantacalcio_full_name']} | "
            f"{r['fantacalcio_club']} | "
            f"TM {r['transfermarkt_player_id']} | "
            f"{r['rule']}"
        )


    print()
    print("================ REVIEW ================")

    if not review:
        print("NESSUNO")
    else:
        for r in review:
            print(
                f"FC {r['fantacalcio_player_id']} | "
                f"{r['fantacalcio_full_name']} | "
                f"{r['fantacalcio_club']} | "
                f"candidate player_id="
                f"{r['existing_player_id'] or '-'} | "
                f"{r['rule']} | "
                f"{r['notes']}"
            )


    print()
    print("================ OUTGOING / INACTIVE ================")

    for r in outgoing:
        print(
            f"{r['player_id']} | "
            f"{r['catalog_name']} | "
            f"{r['catalog_club']} | "
            f"{r['recommended_action']}"
        )


    print()
    print(f"REPORT: {REPORT_FILE}")
    print(f"ALL:    {ALL_FILE}")
    print(f"NEW:    {NEW_FILE}")
    print(f"OUT:    {OUTGOING_FILE}")


if __name__ == "__main__":
    try:
        main()

    except Exception as exc:
        print()
        print(f"ERRORE FATALE: {exc}")
        sys.exit(1)
