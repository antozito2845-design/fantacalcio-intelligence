"""Deterministic local catalog-to-Transfermarkt identity matching.

No network access and no database writes. Transfermarkt/Fantamarkt is identity
provenance only and never changes reconciliation or fantasy roles.
"""

from pathlib import Path
import json
import re
import unicodedata

import pandas as pd

ROOT = Path(__file__).resolve().parents[2]
IDENTITY = ROOT / "data" / "identity"
FBREF = ROOT / "data" / "fbref"
MASTER = IDENTITY / "transfermarkt-serie-a-2026-2027.csv"
CATALOG = FBREF / "catalog-2026-2027.csv"
MAPPINGS = FBREF / "catalog-source-mappings-2026-2027.csv"
PREVIOUS = IDENTITY / "player-identity-enrichment.csv"
COUNTRY_CODES = {
    "albania": "AL", "angola": "AO", "argentina": "AR", "austria": "AT",
    "bosnia erzegovina": "BA", "brasile": "BR", "capo verde": "CV",
    "francia": "FR", "germania": "DE", "ghana": "GH", "grecia": "GR",
    "inghilterra": "GB", "italia": "IT", "marocco": "MA", "nigeria": "NG",
    "olanda": "NL", "portogallo": "PT", "rd del congo": "CD", "scozia": "GB",
    "senegal": "SN", "spagna": "ES", "stati uniti": "US", "suriname": "SR",
    "svizzera": "CH", "turchia": "TR", "uruguay": "UY", "zambia": "ZM",
}
EXPECTED_TEAMS = {"atalanta", "bologna", "cagliari", "como", "fiorentina", "frosinone",
                  "genoa", "inter", "juventus", "lazio", "lecce", "milan", "monza", "napoli",
                  "parma", "roma", "sassuolo", "torino", "udinese", "venezia"}


def normalize(value):
    value = unicodedata.normalize("NFKD", str(value or "").strip().lower())
    value = "".join(char for char in value if not unicodedata.combining(char))
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", value)).strip()


def parse_transfermarkt_id(value):
    match = re.fullmatch(r"TRANSFERMARKT-([1-9][0-9]*)", str(value or "").strip())
    return match.group(1) if match else ""


def height_cm(value):
    match = re.fullmatch(r"([12]),([0-9]{2}) m", str(value or "").strip())
    return int(match.group(1)) * 100 + int(match.group(2)) if match else None


def audit_master(master):
    required = {"transfermarkt_player_id", "team", "full_name", "date_of_birth",
                "nationality", "player_url", "source", "season"}
    missing = required - set(master.columns)
    if missing:
        raise ValueError(f"Missing columns: {sorted(missing)}")
    failures = []
    if len(master) != 638: failures.append("rows!=638")
    if set(master.team) != EXPECTED_TEAMS: failures.append("invalid-team-set")
    if master.transfermarkt_player_id.eq("").any(): failures.append("empty-id")
    if master.transfermarkt_player_id.nunique() != len(master): failures.append("duplicate-id")
    if master.full_name.eq("").any(): failures.append("empty-full-name")
    if master.date_of_birth.eq("").any() or not master.date_of_birth.str.fullmatch(r"\d{4}-\d{2}-\d{2}").all():
        failures.append("invalid-dob")
    try:
        pd.to_datetime(master.date_of_birth, format="%Y-%m-%d", errors="raise")
    except ValueError:
        failures.append("invalid-calendar-date")
    if master.nationality.eq("").any(): failures.append("empty-nationality")
    if not master.season.eq("2026/2027").all(): failures.append("invalid-season")
    if not master.source.eq("TRANSFERMARKT").all(): failures.append("invalid-source")
    expected_url = master.apply(lambda row: f"/spieler/{row.transfermarkt_player_id}" in row.player_url, axis=1)
    if not expected_url.all(): failures.append("url-id-mismatch")
    if failures:
        raise ValueError("Master audit failed: " + ", ".join(failures))
    return {"teams": 20, "rows": len(master), "unique_ids": master.transfermarkt_player_id.nunique(),
            "birth_dates": master.date_of_birth.ne("").sum(),
            "nationalities": master.nationality.ne("").sum(),
            "positions": master.position.ne("").sum(), "heights": master.height.ne("").sum()}


def verified_aliases(mappings):
    result = {}
    verified = mappings[mappings.verified.astype(str).str.lower().isin({"t", "true", "1"})]
    for row in verified.to_dict("records"):
        result.setdefault(str(row["player_id"]), []).append(row)
    return result


def run(catalog, mappings, master, previous=None, output_dir=None, validate_master=True):
    if validate_master:
        audit_master(master)
    aliases = verified_aliases(mappings)
    by_id = {str(row["transfermarkt_player_id"]): row for row in master.to_dict("records")}
    by_name_club = {}
    by_surname_club = {}
    for row in master.to_dict("records"):
        key = (normalize(row["full_name"]), normalize(row["team"]))
        by_name_club.setdefault(key, []).append(row)
        by_surname_club.setdefault((normalize(row["full_name"]).split()[-1], normalize(row["team"])), []).append(row)
    previous_by_player = ({str(row["player_id"]): row for row in previous.to_dict("records")}
                          if previous is not None else {})
    rows = []
    for player in catalog.to_dict("records"):
        player_id = str(player["player_id"])
        club = normalize(player["current_club"])
        local = aliases.get(player_id, [])
        stable_ids = {parse_transfermarkt_id(row["external_id"]) for row in local
                      if row["source"] == "FANTAMARKT" and parse_transfermarkt_id(row["external_id"])}
        method = status = reason = ""
        candidates = []
        if len(stable_ids) > 1:
            status, method, reason = "REVIEW_CONFLICT", "STABLE_ID_CONFLICT", "Più ID verificati per lo stesso player"
        elif len(stable_ids) == 1:
            stable_id = next(iter(stable_ids))
            candidates = [by_id[stable_id]] if stable_id in by_id else []
            if candidates:
                status, method, reason = "MATCHED_HIGH", "MATCHED_HIGH_BY_STABLE_ID", "Mapping FANTAMARKT verificato con ID deterministico"
            else:
                status, method, reason = "REVIEW_CONFLICT", "STABLE_ID_NOT_IN_MASTER", "ID verificato assente dal master"
        else:
            names = {row["external_name"] for row in local if len(normalize(row["external_name"]).split()) >= 2}
            if len(normalize(player["display_name"]).split()) >= 2:
                names.add(player["display_name"])
            candidates = [candidate for name in names for candidate in by_name_club.get((normalize(name), club), [])]
            candidates = list({candidate["transfermarkt_player_id"]: candidate for candidate in candidates}.values())
            if len(candidates) == 1:
                status, method, reason = "MATCHED_HIGH", "MATCHED_HIGH_BY_FULL_NAME_CLUB", "Nome completo verificato, club coerente, candidato unico"
            elif len(candidates) > 1:
                status, method, reason = "REVIEW_AMBIGUOUS", "AMBIGUOUS_FULL_NAME_CLUB", "Più candidati per nome completo e club"
            else:
                old = previous_by_player.get(player_id, {})
                old_name, old_birth = old.get("canonical_full_name", ""), old.get("birth_date", "")
                dob_candidates = [candidate for candidate in by_name_club.get((normalize(old_name), club), [])
                                  if old_birth and candidate["date_of_birth"] == old_birth]
                if len(dob_candidates) == 1:
                    candidates = dob_candidates
                    status, method, reason = "MATCHED_HIGH", "MATCHED_HIGH_BY_ALIAS_DOB", "Alias verificato, DOB indipendente e club coerenti"
                elif len(dob_candidates) > 1:
                    candidates = dob_candidates
                    status, method, reason = "REVIEW_CONFLICT", "ALIAS_DOB_COLLISION", "Alias e DOB convergono su più candidati"
                else:
                    surname_candidates = by_surname_club.get((normalize(player["display_name"]).split()[-1], club), [])
                    candidates = surname_candidates
                    status = "REVIEW_AMBIGUOUS" if surname_candidates else "UNMATCHED"
                    method = "SURNAME_CLUB_NOT_SUFFICIENT" if surname_candidates else "NO_CANDIDATE"
                    reason = "Cognome e club non sono prova sufficiente" if surname_candidates else "Nessun candidato locale"
        candidate = candidates[0] if len(candidates) == 1 else None
        rows.append({
            "player_id": player_id, "catalog_name": player["display_name"], "catalog_club": player["current_club"],
            "fantasy_role": player["fantasy_role"], "transfermarkt_player_id": candidate["transfermarkt_player_id"] if candidate else "",
            "transfermarkt_full_name": candidate["full_name"] if candidate else "",
            "date_of_birth": candidate["date_of_birth"] if candidate else "",
            "nationality": candidate["nationality"] if candidate else "",
            "height_cm": height_cm(candidate["height"]) if candidate else "", "preferred_foot": "",
            "match_category": method, "match_status": status,
            "confidence": "HIGH" if status == "MATCHED_HIGH" else ("LOW" if status.startswith("REVIEW") else "NONE"),
            "evidence": json.dumps({"reason": reason, "candidate_ids": [c["transfermarkt_player_id"] for c in candidates]},
                                   ensure_ascii=False, separators=(",", ":")),
            "source": "TRANSFERMARKT", "source_url": candidate["player_url"] if candidate else "",
            "observed_at": candidate["observed_at"] if candidate else "",
        })
    output = pd.DataFrame(rows)
    # Global one-to-one invariant. No automatic row survives a collision.
    high = output[output.match_status == "MATCHED_HIGH"]
    duplicate_ids = set(high.loc[high.transfermarkt_player_id.duplicated(False), "transfermarkt_player_id"])
    if duplicate_ids:
        mask = output.transfermarkt_player_id.isin(duplicate_ids)
        output.loc[mask, ["match_status", "match_category", "confidence"]] = ["REVIEW_CONFLICT", "ONE_ID_MULTIPLE_PLAYERS", "LOW"]
    safe = output[output.match_status == "MATCHED_HIGH"].copy()
    if output_dir:
        output.to_csv(output_dir / "catalog-transfermarkt-match-2026-2027.csv", index=False, encoding="utf-8")
        safe.to_csv(output_dir / "catalog-transfermarkt-safe-import-2026-2027.csv", index=False, encoding="utf-8")
    return output, safe


def build_identity_csv(matches, output_dir):
    profiles = []
    for row in matches.to_dict("records"):
        high = row["match_status"] == "MATCHED_HIGH"
        canonical = row["transfermarkt_full_name"] if high else ""
        tokens = canonical.split()
        nationality = row["nationality"] if high else ""
        primary_country = nationality.split(";")[0].strip()
        code = COUNTRY_CODES.get(normalize(primary_country), "") if high else ""
        if high:
            identity_status = "VERIFIED_COMPLETE" if code else "VERIFIED_PARTIAL"
        elif row["match_status"].startswith("REVIEW"):
            identity_status = "REVIEW_REQUIRED"
        else:
            identity_status = "DATA_SOURCE_MISSING"
        external_id = row["transfermarkt_player_id"] if high else ""
        profiles.append({
            "player_id": row["player_id"], "canonical_full_name": canonical,
            "first_name": " ".join(tokens[:-1]) if len(tokens) >= 2 else "",
            "last_name": tokens[-1] if tokens else "", "birth_date": row["date_of_birth"] if high else "",
            "nationality": nationality, "nationality_code": code,
            "height_cm": row["height_cm"] if high else "", "preferred_foot": "",
            "transfermarkt_player_id": external_id, "identity_status": identity_status,
            "confidence": "HIGH" if high else ("LOW" if identity_status == "REVIEW_REQUIRED" else "NONE"),
            "source": "TRANSFERMARKT", "source_external_id": external_id,
            "source_url": row["source_url"] if high else "", "evidence": row["evidence"],
            "match_method": row["match_category"],
        })
    profiles = pd.DataFrame(profiles)
    profiles.to_csv(output_dir / "player-identity-enrichment.csv", index=False, encoding="utf-8")
    profiles[profiles.confidence.eq("HIGH")].to_csv(
        output_dir / "player-identity-safe-import.csv", index=False, encoding="utf-8")
    return profiles


def main():
    catalog = pd.read_csv(CATALOG, dtype=str, keep_default_na=False)
    mappings = pd.read_csv(MAPPINGS, dtype=str, keep_default_na=False)
    master = pd.read_csv(MASTER, dtype=str, keep_default_na=False)
    previous = pd.read_csv(PREVIOUS, dtype=str, keep_default_na=False) if PREVIOUS.exists() else None
    output, safe = run(catalog, mappings, master, previous, IDENTITY)
    profiles = build_identity_csv(output, IDENTITY)
    print(output.match_category.value_counts().to_string())
    print(f"HIGH={len(safe)} REVIEW={output.match_status.str.startswith('REVIEW').sum()} UNMATCHED={(output.match_status == 'UNMATCHED').sum()}")
    print(profiles.identity_status.value_counts().to_string())


if __name__ == "__main__":
    main()
