from __future__ import annotations

import csv
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "data" / "statistics" / "features"

RECOVERED = {
    "1032", "1033", "1034", "1035",
    "1036", "1037", "1038", "1111",
}

OLD_BASE = OUT / "player-season-performance-base.csv"
NEW_BASE = OUT / "b66-player-season-performance-base.csv"

OLD_PROFILE = OUT / "player-performance-import.csv"
NEW_PROFILE = OUT / "b66-player-performance-import.csv"


def read(path):
    with path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        return list(csv.DictReader(stream))


old_base = {
    (r["player_id"], r["season"]): r
    for r in read(OLD_BASE)
    if r["player_id"] not in RECOVERED
}

new_base = {
    (r["player_id"], r["season"]): r
    for r in read(NEW_BASE)
    if r["player_id"] not in RECOVERED
}

shared = sorted(
    set(old_base) & set(new_base)
)

if not shared:
    raise RuntimeError(
        "Nessuna season-base condivisa B49/B66."
    )

BASE_FIELDS = [
    "season_source",
    "domestic_league_available",
    "domestic_competitions",
    "domestic_clubs",
    "supplemental_competition_rows",
    "fantacalcio_voted_appearances",
    "transfermarkt_appearances",
    "minutes_played",
    "goals",
    "assists",
    "yellow_cards",
    "second_yellow_cards",
    "red_cards",
    "average_rating",
    "fantasy_average",
    "goals_conceded",
    "clean_sheets",
    "saves",
    "penalties_saved",
    "goals_per_90",
    "assists_per_90",
    "goal_contributions_per_90",
    "discipline_events_per_90",
    "input_rows",
]

base_differences = []

for key in shared:
    old = old_base[key]
    new = new_base[key]

    for field in BASE_FIELDS:
        if field not in old or field not in new:
            continue

        if old[field] != new[field]:
            base_differences.append(
                (key, field, old[field], new[field])
            )

if base_differences:
    print("Prime differenze season-base:")
    for difference in base_differences[:20]:
        print(difference)

    raise RuntimeError(
        f"B66 semantic drift season-base: "
        f"{len(base_differences)} differenze"
    )


old_profiles = {
    r["player_id"]: r
    for r in read(OLD_PROFILE)
    if r["player_id"] not in RECOVERED
}

new_profiles = {
    r["player_id"]: r
    for r in read(NEW_PROFILE)
    if r["player_id"] not in RECOVERED
}

profile_shared = sorted(
    set(old_profiles) & set(new_profiles),
    key=int,
)

PROFILE_FIELDS = [
    "history_seasons_available",
    "latest_history_season",
    "source_mix",
    "data_confidence_score",
]

profile_differences = []

for player_id in profile_shared:
    old = old_profiles[player_id]
    new = new_profiles[player_id]

    for field in PROFILE_FIELDS:
        if field not in old or field not in new:
            continue

        if old[field] != new[field]:
            profile_differences.append(
                (
                    player_id,
                    field,
                    old[field],
                    new[field],
                )
            )

    old_json = json.loads(
        old["feature_values_json"]
    )

    new_json = json.loads(
        new["feature_values_json"]
    )

    shared_features = (
        set(old_json)
        & set(new_json)
    )

    for feature in shared_features:

        # Popolazione relativa: può cambiare.
        if "percentile" in feature.lower():
            continue

        if old_json[feature] != new_json[feature]:
            profile_differences.append(
                (
                    player_id,
                    feature,
                    old_json[feature],
                    new_json[feature],
                )
            )

if profile_differences:
    print("Prime differenze profili:")
    for difference in profile_differences[:20]:
        print(difference)

    raise RuntimeError(
        f"B66 semantic drift profili: "
        f"{len(profile_differences)} differenze"
    )


print("=== B66 SEMANTIC GATE ===")
print(f"shared_season_bases={len(shared)}")
print(f"shared_profiles={len(profile_shared)}")
print("semantic_drift=0")
print("PASS")