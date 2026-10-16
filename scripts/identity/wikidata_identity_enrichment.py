"""Conservative identity enrichment from local verified aliases + Wikidata API.

Uses exact Wikipedia titles in small batches (no scraping/search crawling). A
profile is HIGH only when Wikidata full name/birth year is corroborated by the
local FBref record with the same full name and year.
"""

from pathlib import Path
import json
import re
import unicodedata
import requests
import pandas as pd

ROOT = Path(__file__).resolve().parents[2]
FBREF = ROOT / "data" / "fbref"
OUTPUT = ROOT / "data" / "identity"
USER_AGENT = "FantacalcioIntelligence/1.0 (identity-enrichment; local research)"
FOOTBALL_PLAYER_QID = "Q937857"


def normalize(value):
    value = unicodedata.normalize("NFKD", str(value or "").strip().lower())
    value = "".join(c for c in value if not unicodedata.combining(c))
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", value)).strip()


def chunks(values, size=40):
    for index in range(0, len(values), size):
        yield values[index:index + size]


def api(params):
    response = requests.get("https://www.wikidata.org/w/api.php", params=params,
                            headers={"User-Agent": USER_AGENT}, timeout=30)
    response.raise_for_status()
    return response.json()


def exact_title_qids(names):
    found = {}
    for site in ("itwiki", "enwiki"):
        for batch in chunks(names):
            payload = api({"action": "wbgetentities", "sites": site,
                           "titles": "|".join(batch), "props": "labels|claims|sitelinks",
                           "languages": "it|en", "format": "json"})
            for qid, entity in payload.get("entities", {}).items():
                if qid == "-1" or "missing" in entity:
                    continue
                labels = entity.get("labels", {})
                label = labels.get("it", labels.get("en", {})).get("value", "")
                matching = [name for name in batch if normalize(name) == normalize(label)]
                for name in matching:
                    found.setdefault(normalize(name), []).append((qid, entity, site))
    return found


def claim_id(entity, property_id):
    claims = entity.get("claims", {}).get(property_id, [])
    values = []
    for claim in claims:
        try:
            values.append(claim["mainsnak"]["datavalue"]["value"]["id"])
        except (KeyError, TypeError):
            pass
    return values


def claim_time(entity, property_id):
    try:
        raw = entity["claims"][property_id][0]["mainsnak"]["datavalue"]["value"]["time"]
        value = raw.lstrip("+")[:10]
        return "" if "-00" in value else value
    except (KeyError, IndexError, TypeError):
        return ""


def claim_quantity(entity, property_id):
    try:
        return entity["claims"][property_id][0]["mainsnak"]["datavalue"]["value"]["amount"].lstrip("+")
    except (KeyError, IndexError, TypeError):
        return ""


def height_cm(entity):
    value = claim_quantity(entity, "P2048")
    if not value:
        return ""
    numeric = float(value)
    return round(numeric * 100 if numeric < 3 else numeric)


def claim_string(entity, property_id):
    try:
        return entity["claims"][property_id][0]["mainsnak"]["datavalue"]["value"]
    except (KeyError, IndexError, TypeError):
        return ""


def load_country_codes(entity_groups):
    qids = sorted({qid for values in entity_groups.values() for _, entity, _ in values
                   for qid in claim_id(entity, "P27")})
    codes = {}
    for batch in chunks(qids):
        try:
            payload = api({"action": "wbgetentities", "ids": "|".join(batch),
                           "props": "claims", "format": "json"})
        except requests.HTTPError as error:
            if error.response is not None and error.response.status_code == 429:
                print("Wikidata rate limit: country code lasciati NULL, nessun retry aggressivo.")
                return {}
            raise
        for qid, entity in payload.get("entities", {}).items():
            code = claim_string(entity, "P297")
            if code:
                codes[qid] = code.upper()
    return codes


def build_aliases(catalog, mappings):
    aliases = {}
    for row in catalog.to_dict("records"):
        if len(normalize(row["display_name"]).split()) >= 2:
            aliases.setdefault(str(row["player_id"]), set()).add(row["display_name"])
    for row in mappings.to_dict("records"):
        if str(row.get("verified", "")).lower() not in {"t", "true", "1"}:
            continue
        name = str(row.get("external_name", "")).strip()
        if len(normalize(name).split()) >= 2:
            aliases.setdefault(str(row["player_id"]), set()).add(name)
    return aliases


def fbref_evidence():
    evidence = {}
    for season in ("2024-2025", "2025-2026"):
        frame = pd.read_csv(FBREF / f"fbref-serie-a-{season}-aggregated-preview.csv",
                            dtype=str, keep_default_na=False)
        for row in frame.to_dict("records"):
            evidence.setdefault(normalize(row["name"]), []).append(row)
    return evidence


def run():
    catalog = pd.read_csv(FBREF / "catalog-2026-2027.csv", dtype=str, keep_default_na=False)
    mappings = pd.read_csv(FBREF / "catalog-source-mappings-2026-2027.csv",
                           dtype=str, keep_default_na=False)
    aliases = build_aliases(catalog, mappings)
    names = sorted({name for values in aliases.values() for name in values})
    entities = exact_title_qids(names)
    country_codes = load_country_codes(entities)
    fbref = fbref_evidence()
    coverage_file = FBREF / "fbref-catalog-history-coverage.csv"
    coverage = (pd.read_csv(coverage_file, dtype=str, keep_default_na=False)
                if coverage_file.exists() else pd.DataFrame())
    history_class = ({str(row.player_id): row.history_classification
                      for row in coverage.itertuples()} if not coverage.empty else {})
    rows = []
    for player in catalog.to_dict("records"):
        player_id = str(player["player_id"])
        candidates = []
        for alias in aliases.get(player_id, set()):
            for qid, entity, site in entities.get(normalize(alias), []):
                if FOOTBALL_PLAYER_QID not in claim_id(entity, "P106"):
                    continue
                birth = claim_time(entity, "P569")
                corroboration = [row for row in fbref.get(normalize(alias), [])
                                 if birth and row.get("born", "") == birth[:4]]
                candidates.append((alias, qid, entity, site, birth, corroboration))
        unique_qids = {candidate[1] for candidate in candidates}
        high = [candidate for candidate in candidates if candidate[5]]
        if len({candidate[1] for candidate in high}) == 1:
            alias, qid, entity, site, birth, corroboration = high[0]
            nationality_ids = claim_id(entity, "P27")
            nationality_codes = sorted({country_codes[qid] for qid in nationality_ids if qid in country_codes})
            status = "VERIFIED_COMPLETE" if len(nationality_codes) == 1 else "VERIFIED_PARTIAL"
            confidence = "HIGH"
            method = "WIKIDATA_EXACT_NAME_FBREF_BIRTH_YEAR"
            evidence_json = {"signals": ["VERIFIED_ALIAS", "WIKIDATA_QID",
                                           "EXACT_FULL_NAME", "FBREF_BIRTH_YEAR"],
                             "fbref_external_ids": sorted({row["external_id"] for row in corroboration})}
            canonical = entity.get("labels", {}).get("it", entity.get("labels", {}).get("en", {})).get("value", alias)
        elif candidates:
            alias, qid, entity, site, birth, _ = candidates[0]
            nationality_ids = claim_id(entity, "P27")
            nationality_codes = sorted({country_codes[qid] for qid in nationality_ids if qid in country_codes})
            status = "REVIEW_REQUIRED"
            confidence = "MEDIUM" if len(unique_qids) == 1 else "LOW"
            method = "WIKIDATA_EXACT_TITLE_UNCORROBORATED"
            evidence_json = {"signals": ["VERIFIED_ALIAS", "EXACT_TITLE"],
                             "conflicts": [] if len(unique_qids) == 1 else ["MULTIPLE_WIKIDATA_IDS"]}
            canonical = entity.get("labels", {}).get("it", entity.get("labels", {}).get("en", {})).get("value", alias)
        else:
            qid = birth = canonical = ""
            nationality_ids = []
            nationality_codes = []
            status = ("NO_SERIE_A_HISTORY_EXPECTED"
                      if history_class.get(player_id) == "NO_HISTORY_EXPECTED"
                      else "DATA_SOURCE_MISSING")
            confidence = "NONE"
            method = "NO_EXACT_WIKIDATA_ENTITY"
            evidence_json = {"signals": [], "conflicts": []}
            site = ""
        tokens = canonical.split()
        rows.append({
            "player_id": player_id,
            "canonical_full_name": canonical,
            "first_name": " ".join(tokens[:-1]) if len(tokens) >= 2 else "",
            "last_name": tokens[-1] if tokens else "",
            "birth_date": birth,
            "nationality_entity_ids": "|".join(nationality_ids),
            "nationality_code": nationality_codes[0] if len(nationality_codes) == 1 else "",
            "height_cm": height_cm(entity) if candidates else "",
            "preferred_foot": "",
            "identity_status": status,
            "confidence": confidence,
            "source": "WIKIDATA",
            "source_external_id": qid,
            "source_url": f"https://www.wikidata.org/wiki/{qid}" if qid else "",
            "match_method": method,
            "evidence": json.dumps(evidence_json, ensure_ascii=False, separators=(",", ":")),
        })
    output = pd.DataFrame(rows)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    output.to_csv(OUTPUT / "player-identity-enrichment.csv", index=False, encoding="utf-8")
    output[output.identity_status.isin(["VERIFIED_COMPLETE", "VERIFIED_PARTIAL"])].to_csv(
        OUTPUT / "player-identity-safe-import.csv", index=False, encoding="utf-8")
    print(output.identity_status.value_counts().to_string())
    print(f"Safe import: {output.identity_status.isin(['VERIFIED_COMPLETE', 'VERIFIED_PARTIAL']).sum()}")


if __name__ == "__main__":
    run()
