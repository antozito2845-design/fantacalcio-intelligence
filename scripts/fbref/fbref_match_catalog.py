"""Safe, local-only FBref-to-catalog matching across multiple seasons.

FBref remains a statistical source. This script never writes the database and
never creates reconciliation/player_source_mappings data.
"""

from pathlib import Path
import re
import unicodedata
import pandas as pd


ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT / "data" / "fbref"
CATALOG_FILE = DATA_DIR / "catalog-2026-2027.csv"
FBREF_FILES = {
    "2024-2025": DATA_DIR / "fbref-serie-a-2024-2025-aggregated-preview.csv",
    "2025-2026": DATA_DIR / "fbref-serie-a-2025-2026-aggregated-preview.csv",
}
IDENTITY_FILE = ROOT / "data" / "identity" / "player-identity-enrichment.csv"
SAFE_METHODS = {"EXACT_FULL_NAME_BIRTH_DATE", "CROSS_SEASON_EXACT_ANCHOR"}


def normalize(value):
    if pd.isna(value):
        return ""
    value = unicodedata.normalize("NFKD", str(value).strip().lower())
    value = "".join(char for char in value if not unicodedata.combining(char))
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9]+", " ", value)).strip()


def surname(value):
    tokens = normalize(value).split()
    return tokens[-1] if tokens else ""


def build_catalog_indexes(catalog, identities=None):
    catalog = catalog.copy()
    catalog["catalog_normalized"] = catalog["normalized_name"].map(normalize)
    missing = catalog["catalog_normalized"].eq("")
    catalog.loc[missing, "catalog_normalized"] = catalog.loc[missing, "display_name"].map(normalize)
    exact, surnames = {}, {}
    for row in catalog.to_dict("records"):
        key = row["catalog_normalized"]
        if key:
            exact.setdefault(key, []).append(row)
            surnames.setdefault(surname(key), []).append(row)
    identity_index = {}
    if identities is not None and not identities.empty:
        verified = identities[
            identities["identity_status"].isin(["VERIFIED_COMPLETE", "VERIFIED_PARTIAL"])
            & identities["confidence"].eq("HIGH")
            & identities["birth_date"].ne("")
        ]
        catalog_by_id = {str(row["player_id"]): row for row in catalog.to_dict("records")}
        for identity in verified.to_dict("records"):
            player = catalog_by_id.get(str(identity["player_id"]))
            key = normalize(identity.get("canonical_full_name", ""))
            if player and len(key.split()) >= 2:
                enriched = dict(player)
                enriched["birth_date"] = str(identity["birth_date"])
                identity_index.setdefault(key, []).append(enriched)
    return catalog, exact, surnames, identity_index


def result(status, method, confidence, reason, candidates=(), signals=(), conflicts=()):
    candidates = list(candidates)
    candidate = candidates[0] if len(candidates) == 1 else None
    return {
        "match_status": status,
        "match_method": method,
        "match_confidence": confidence,
        "match_reason": reason,
        "match_signals": "|".join(signals),
        "match_conflicts": "|".join(conflicts),
        "candidate_player_id": "" if candidate is None else candidate["player_id"],
        "candidate_name": " | ".join(str(item["display_name"]) for item in candidates),
        "candidate_club": " | ".join(str(item["current_club"]) for item in candidates),
        "candidate_role": "" if candidate is None else candidate["fantasy_role"],
    }


def initial_match(row, exact_index, surname_index, identity_index):
    full_name = normalize(row.get("name", ""))
    born = str(row.get("born", "")).strip()
    enriched = [candidate for candidate in identity_index.get(full_name, [])
                if born and str(candidate["birth_date"])[:4] == born]
    if len(enriched) == 1:
        return result("MATCHED", "EXACT_FULL_NAME_BIRTH_DATE", "HIGH",
                      "Nome completo e anno di nascita esatti e univoci",
                      enriched, ["EXACT_FULL_NAME", "EXACT_BIRTH_YEAR"])
    if len(enriched) > 1:
        return result("REVIEW", "AMBIGUOUS_NAME_BIRTH_DATE", "LOW",
                      "Nome e anno di nascita non identificano un solo player",
                      enriched, ["EXACT_FULL_NAME", "EXACT_BIRTH_YEAR"],
                      ["MULTIPLE_CATALOG_PLAYERS"])
    exact = exact_index.get(full_name, [])
    # Il nome, anche completo, e' un solo segnale e richiede revisione senza DOB.
    if len(full_name.split()) >= 2 and len(exact) == 1:
        return result("REVIEW", "EXACT_NAME_ONLY", "MEDIUM",
                      "Nome completo esatto ma data di nascita non verificata",
                      exact, ["EXACT_FULL_NAME"], ["BIRTH_DATE_NOT_CONFIRMED"])
    if len(exact) > 1:
        return result("REVIEW", "AMBIGUOUS_EXACT_NAME", "LOW", "Nome completo non univoco",
                      exact, ["EXACT_FULL_NAME"], ["MULTIPLE_CATALOG_PLAYERS"])
    candidates = surname_index.get(surname(full_name), [])
    if candidates:
        method = "SURNAME_ONLY_CANDIDATE" if len(candidates) == 1 else "AMBIGUOUS_SURNAME"
        return result("REVIEW", method, "LOW", "Il solo cognome non è prova di identità",
                      candidates, ["SURNAME_ONLY"], ["FULL_NAME_NOT_CONFIRMED"])
    return result("UNMATCHED", "NO_SAFE_CANDIDATE", "NONE",
                  "Nessun candidato deterministico", signals=[])


def apply_cross_season_continuity(frames):
    combined = pd.concat(frames.values(), keys=frames.keys(), names=["season_key", "row_index"])
    for external_id, rows in combined.groupby("external_id", sort=False):
        anchors = rows[(rows.match_status == "MATCHED")
                       & (rows.match_method == "EXACT_FULL_NAME_BIRTH_DATE")]
        anchor_ids = set(anchors.candidate_player_id.astype(str)) - {""}
        if len(anchor_ids) == 1:
            anchor = anchors.iloc[0]
            for season_key, row_index in rows.index:
                frame = frames[season_key]
                if frame.at[row_index, "match_status"] != "MATCHED":
                    frame.at[row_index, "match_status"] = "MATCHED"
                    frame.at[row_index, "match_method"] = "CROSS_SEASON_EXACT_ANCHOR"
                    frame.at[row_index, "match_confidence"] = "HIGH"
                    frame.at[row_index, "match_reason"] = "Stesso FBref ID di un nome completo esatto in altra stagione"
                    frame.at[row_index, "match_signals"] = "SAME_FBREF_ID|EXACT_FULL_NAME_OTHER_SEASON"
                    for column in ("candidate_player_id", "candidate_name", "candidate_club", "candidate_role"):
                        frame.at[row_index, column] = anchor[column]
        elif len(anchor_ids) > 1:
            for season_key, row_index in rows.index:
                frame = frames[season_key]
                frame.at[row_index, "match_status"] = "REVIEW"
                frame.at[row_index, "match_method"] = "CROSS_SEASON_CONFLICT"
                frame.at[row_index, "match_confidence"] = "LOW"
                frame.at[row_index, "match_reason"] = "Lo stesso FBref ID produce anchor catalogo discordanti"
                frame.at[row_index, "match_conflicts"] = "MULTIPLE_PLAYER_IDS_FOR_FBREF_ID"


def reject_player_collisions(frames):
    combined = pd.concat(frames.values(), keys=frames.keys(), names=["season_key", "row_index"])
    matched = combined[(combined.match_status == "MATCHED") & (combined.candidate_player_id.astype(str) != "")]
    for player_id, rows in matched.groupby(matched.candidate_player_id.astype(str), sort=False):
        if rows.external_id.nunique() <= 1:
            continue
        for season_key, row_index in rows.index:
            frame = frames[season_key]
            frame.at[row_index, "match_status"] = "REVIEW"
            frame.at[row_index, "match_method"] = "PLAYER_ID_EXTERNAL_ID_COLLISION"
            frame.at[row_index, "match_confidence"] = "LOW"
            frame.at[row_index, "match_reason"] = "FBref IDs differenti convergono sullo stesso player_id"
            frame.at[row_index, "match_conflicts"] = "MULTIPLE_FBREF_IDS_FOR_PLAYER_ID"


def safe_import_columns(frame):
    allowed = ["external_id", "name", "clubs_played", "appearances", "starts", "minutes_played",
               "goals", "assists", "penalties_scored", "penalties_taken", "penalties_missed",
               "yellow_cards", "red_cards", "expected_goals", "expected_assists",
               "candidate_player_id", "match_status", "match_method", "match_confidence"]
    return frame[[column for column in allowed if column in frame.columns]].copy()


def classify_catalog_coverage(catalog, frames):
    candidates = {}
    historical_clubs = set()
    for frame in frames.values():
        historical_clubs.update(frame.clubs_played.fillna("").str.split("|").explode().map(normalize))
        for row in frame.itertuples():
            player_id = str(row.candidate_player_id)
            if player_id:
                candidates.setdefault(player_id, set()).add(row.Index if hasattr(row, "Index") else 0)
    output = catalog.copy()
    output["coverage_seasons"] = output.player_id.astype(str).map(
        lambda player_id: sum(((frame.match_status == "MATCHED")
                               & frame.candidate_player_id.astype(str).eq(player_id)).any()
                              for frame in frames.values()))
    output["history_classification"] = output.apply(
        lambda row: "CANDIDATE_PRESENT" if row.coverage_seasons else (
            "NO_HISTORY_EXPECTED" if normalize(row.current_club) not in historical_clubs
            else "INSUFFICIENT_MATCHING_DATA"), axis=1)
    return output


def run(catalog, season_frames, output_dir=None, identities=None):
    catalog, exact, surnames, identity_index = build_catalog_indexes(catalog, identities)
    results = {}
    for season, frame in season_frames.items():
        frame = frame.copy().reset_index(drop=True)
        generated_columns = {
            "match_status", "match_method", "match_confidence", "match_reason",
            "match_signals", "match_conflicts", "candidate_player_id",
            "candidate_name", "candidate_club", "candidate_role",
        }
        frame.drop(columns=[column for column in generated_columns if column in frame], inplace=True)
        matches = pd.DataFrame([initial_match(row, exact, surnames, identity_index)
                                for row in frame.to_dict("records")])
        results[season] = pd.concat([frame, matches], axis=1)
    apply_cross_season_continuity(results)
    reject_player_collisions(results)
    coverage = classify_catalog_coverage(catalog, results)
    if output_dir:
        output_dir.mkdir(parents=True, exist_ok=True)
        for season, frame in results.items():
            frame.to_csv(output_dir / f"fbref-{season}-catalog-matching.csv", index=False, encoding="utf-8")
            for status in ("MATCHED", "REVIEW", "UNMATCHED"):
                frame[frame.match_status == status].to_csv(
                    output_dir / f"fbref-{season}-{status.lower()}.csv", index=False, encoding="utf-8")
            frame[frame.match_method.str.contains("CONFLICT|COLLISION", regex=True, na=False)].to_csv(
                output_dir / f"fbref-{season}-duplicate-matches.csv", index=False, encoding="utf-8")
            safe_import_columns(frame[frame.match_status == "MATCHED"]).to_csv(
                output_dir / f"fbref-{season}-safe-import.csv", index=False, encoding="utf-8")
        coverage.to_csv(output_dir / "fbref-catalog-history-coverage.csv", index=False, encoding="utf-8")
    return results, coverage


def main():
    catalog = pd.read_csv(CATALOG_FILE, dtype=str, keep_default_na=False)
    frames = {season: pd.read_csv(path, dtype=str, keep_default_na=False)
              for season, path in FBREF_FILES.items()}
    identities = (pd.read_csv(IDENTITY_FILE, dtype=str, keep_default_na=False)
                  if IDENTITY_FILE.exists() else None)
    results, coverage = run(catalog, frames, DATA_DIR, identities)
    print(f"Catalogo operativo: {len(catalog)}")
    for season, frame in results.items():
        counts = frame.match_status.value_counts()
        print(f"{season}: total={len(frame)} MATCHED={counts.get('MATCHED', 0)} "
              f"REVIEW={counts.get('REVIEW', 0)} UNMATCHED={counts.get('UNMATCHED', 0)}")
    print(f"Coperti almeno una stagione: {(coverage.coverage_seasons >= 1).sum()}")
    print(f"Coperti entrambe: {(coverage.coverage_seasons == 2).sum()}")
    print(f"Senza candidato: {(coverage.coverage_seasons == 0).sum()}")
    print("Nessuna scrittura eseguita nel database.")


if __name__ == "__main__":
    main()
