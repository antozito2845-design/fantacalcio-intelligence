"""Build one complete FBref player-season record from local exported tables.

No network access. Tables are merged by stable FBref external_id before import,
so a later table can never null-out fields imported from an earlier table.
"""

import argparse
from pathlib import Path
import pandas as pd


IDENTITY_COLUMNS = {"external_id", "name", "nation", "positions", "born"}
ADDITIVE_COLUMNS = {
    "appearances", "starts", "minutes_played", "goals", "assists",
    "penalties_scored", "penalties_taken", "penalties_missed",
    "yellow_cards", "red_cards", "goals_conceded", "clean_sheets",
    "saves", "penalties_saved", "expected_goals", "expected_assists",
}


def non_blank(values):
    return next((str(value).strip() for value in values if str(value).strip()), "")


def aggregate_table(frame):
    if "external_id" not in frame or frame.external_id.astype(str).str.strip().eq("").any():
        raise ValueError("Ogni riga FBref deve avere external_id")
    rows = []
    for external_id, group in frame.groupby("external_id", sort=False):
        row = {"external_id": external_id}
        for column in frame.columns:
            if column == "external_id":
                continue
            if column == "club" or column == "clubs_played":
                clubs = []
                for value in group[column].fillna(""):
                    for club in str(value).split("|"):
                        if club.strip() and club.strip() not in clubs:
                            clubs.append(club.strip())
                row["clubs_played"] = "|".join(clubs)
            elif column in ADDITIVE_COLUMNS:
                numeric = pd.to_numeric(group[column].replace("", pd.NA), errors="coerce")
                row[column] = "" if numeric.notna().sum() == 0 else numeric.sum()
            elif column.endswith("_per90"):
                continue
            else:
                row[column] = non_blank(group[column])
        minutes = pd.to_numeric(pd.Series([row.get("minutes_played", "")]), errors="coerce").iloc[0]
        if pd.notna(minutes) and minutes > 0:
            for metric in ("goals", "assists"):
                value = pd.to_numeric(pd.Series([row.get(metric, "")]), errors="coerce").iloc[0]
                row[f"{metric}_per90"] = "" if pd.isna(value) else round(value * 90 / minutes, 4)
        rows.append(row)
    return pd.DataFrame(rows)


def merge_tables(frames):
    merged = None
    for name, frame in frames.items():
        aggregated = aggregate_table(frame)
        if merged is None:
            merged = aggregated
            continue
        overlaps = (set(merged.columns) & set(aggregated.columns)) - {"external_id", "clubs_played"}
        merged = merged.merge(aggregated, on="external_id", how="outer", suffixes=("", f"_{name}"))
        for column in overlaps:
            alternative = f"{column}_{name}"
            if alternative in merged:
                left = merged[column].replace("", pd.NA)
                right = merged[alternative].replace("", pd.NA)
                conflicts = left.notna() & right.notna() & left.astype(str).ne(right.astype(str))
                if conflicts.any():
                    raise ValueError(f"Valori discordanti per {column} nella tabella {name}")
                merged[column] = left.fillna(right).fillna("")
                merged.drop(columns=[alternative], inplace=True)
        alternative_clubs = f"clubs_played_{name}"
        if alternative_clubs in merged:
            merged["clubs_played"] = merged.apply(
                lambda row: "|".join(dict.fromkeys(
                    club for value in (row.get("clubs_played", ""), row.get(alternative_clubs, ""))
                    for club in str(value).split("|") if club and club != "nan")), axis=1)
            merged.drop(columns=[alternative_clubs], inplace=True)
    return merged if merged is not None else pd.DataFrame()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--table", action="append", required=True, metavar="NAME=CSV")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    frames = {}
    for item in args.table:
        name, path = item.split("=", 1)
        frames[name] = pd.read_csv(path, dtype=str, keep_default_na=False)
    output = merge_tables(frames)
    Path(args.output).parent.mkdir(parents=True, exist_ok=True)
    output.to_csv(args.output, index=False, encoding="utf-8")
    print(f"Creato {args.output}: {len(output)} giocatori FBref unici")


if __name__ == "__main__":
    main()
