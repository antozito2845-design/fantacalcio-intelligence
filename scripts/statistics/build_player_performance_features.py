#!/usr/bin/env python3
"""Build deterministic, source-aware Block 49 performance features."""
from __future__ import annotations

import csv
import hashlib
import json
import math
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "statistics"
OUT = DATA / "features"
VERSION = "B49_FEATURES_V1"
SEASONS = ("2024/2025", "2025/2026")
PLAYOFF_MARKERS = ("play-off", "playoff", "relegation", "promotion")


def read(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def write(path: Path, rows, fields=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = fields or list(rows[0])
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields, quoting=csv.QUOTE_ALL)
        writer.writeheader(); writer.writerows(rows)


def integer(value):
    return None if value in (None, "") else int(value)


def decimal(value):
    return None if value in (None, "") else float(value)


def strict_sum(rows, field):
    values = [integer(row[field]) for row in rows]
    return None if not values or any(value is None for value in values) else sum(values)


def loose_sum(rows, field):
    values = [integer(row[field]) for row in rows if row[field] != ""]
    return None if not values else sum(values)


def csv_number(value):
    """Preserve unknown as CSV NULL instead of serialising Python's 'None'."""
    return "" if value is None else str(value)


def is_domestic_league(row):
    return row["competition_type"] == "DOMESTIC_LEAGUE" and not any(
        marker in row["competition_name"].lower() for marker in PLAYOFF_MARKERS
    )


def aggregate_tm_league(rows):
    """Aggregate multi-club domestic-league rows without converting unknown to zero."""
    league = [row for row in rows if is_domestic_league(row)]
    if not league:
        return None
    fields = ("appearances", "minutes_played", "goals", "assists", "yellow_cards",
              "second_yellow_cards", "red_cards")
    return {field: strict_sum(league, field) for field in fields} | {"rows": league}


def per90(value, minutes):
    return None if value is None or minutes is None or minutes <= 0 else round(value * 90.0 / minutes, 4)


def quantile(values, q):
    values = sorted(values)
    if not values: return None
    position = (len(values) - 1) * q
    lo, hi = math.floor(position), math.ceil(position)
    return values[lo] if lo == hi else values[lo] + (values[hi] - values[lo]) * (position - lo)


def percentile_map(items):
    """Inclusive 0-100 percentile rank with average ranks for ties."""
    by_value = defaultdict(list)
    for key, value in items: by_value[value].append(key)
    ordered = sorted(by_value)
    count = sum(len(by_value[value]) for value in ordered)
    result, cursor = {}, 0
    for value in ordered:
        size = len(by_value[value]); average_index = cursor + (size - 1) / 2
        percentile = 50.0 if count == 1 else round(100 * average_index / (count - 1), 2)
        for key in by_value[value]: result[key] = percentile
        cursor += size
    return result


def json_value(value):
    return value if value is not None else None


def main():
    context = {row["player_id"]: row for row in read(OUT / "current-player-feature-context.csv")}
    coverage = {row["player_id"]: row for row in read(DATA / "current-2026-2027-historical-coverage.csv")}
    if len(context) != 495 or set(context) != set(coverage):
        raise RuntimeError("Current 495 context/coverage mismatch")

    fanta_by_id_season = {}
    for filename, season in (("fantacalcio-serie-a-2024-25.csv", "2024/2025"),
                             ("fantacalcio-serie-a-2025-26.csv", "2025/2026")):
        for row in read(DATA / "fantacalcio" / filename):
            fanta_by_id_season[(row["external_id"], season)] = row
    tm_groups = defaultdict(list)
    for row in read(DATA / "international" / "transfermarkt-international-statistics.csv"):
        if row["player_id"] in context:
            tm_groups[(row["player_id"], row["season_code"])].append(row)

    bases = []
    for player_id in sorted(context, key=int):
        cov, ctx = coverage[player_id], context[player_id]
        for season, suffix in (("2024/2025", "2024_25"), ("2025/2026", "2025_26")):
            base = {
                "player_id": player_id, "player_name": ctx["player_name"], "current_role": ctx["current_role"],
                "season": season, "season_source": "", "domestic_league_available": "NO",
                "domestic_competitions": "", "domestic_clubs": "", "supplemental_competition_rows": "0",
                "fantacalcio_voted_appearances": "", "transfermarkt_appearances": "",
                "minutes_played": "", "goals": "", "assists": "", "yellow_cards": "",
                "second_yellow_cards": "", "red_cards": "", "average_rating": "", "fantasy_average": "",
                "goals_conceded": "", "clean_sheets": "", "saves": "", "penalties_saved": "",
                "goals_per_90": "", "assists_per_90": "", "goal_contributions_per_90": "",
                "discipline_events_per_90": "", "input_rows": "0",
            }
            if cov[f"has_fantacalcio_{suffix}"] == "YES":
                source = fanta_by_id_season.get((cov["fantacalcio_id"], season))
                if not source: raise RuntimeError(f"Missing Fantacalcio source row: {player_id} {season}")
                base.update({
                    "season_source": "FANTACALCIO", "domestic_league_available": "YES",
                    "domestic_competitions": "Serie A", "domestic_clubs": source["club"], "input_rows": "1",
                    "fantacalcio_voted_appearances": source["appearances"], "goals": source["goals"],
                    "assists": source["assists"], "yellow_cards": source["yellow_cards"],
                    "red_cards": source["red_cards"], "average_rating": source["average_rating"],
                    "fantasy_average": source["fantasy_average"], "goals_conceded": source["goals_conceded"],
                    "clean_sheets": source["clean_sheets"], "saves": source["saves"],
                    "penalties_saved": source["penalties_saved"],
                })
            else:
                rows = tm_groups.get((player_id, season), [])
                aggregate = aggregate_tm_league(rows)
                league = [] if aggregate is None else aggregate["rows"]
                supplemental = [row for row in rows if row not in league]
                if league:
                    minutes, goals, assists = aggregate["minutes_played"], aggregate["goals"], aggregate["assists"]
                    yellow, second_yellow, red = aggregate["yellow_cards"], aggregate["second_yellow_cards"], aggregate["red_cards"]
                    discipline = None if any(v is None for v in (yellow, second_yellow, red)) else yellow + second_yellow + red
                    base.update({
                        "season_source": "TRANSFERMARKT", "domestic_league_available": "YES",
                        "domestic_competitions": "|".join(sorted({r["competition_name"] for r in league})),
                        "domestic_clubs": "|".join(sorted({r["club_name"] for r in league})),
                        "supplemental_competition_rows": str(len(supplemental)), "input_rows": str(len(rows)),
                        "transfermarkt_appearances": csv_number(aggregate["appearances"]),
                        "minutes_played": csv_number(minutes), "goals": csv_number(goals),
                        "assists": csv_number(assists), "yellow_cards": csv_number(yellow),
                        "second_yellow_cards": csv_number(second_yellow), "red_cards": csv_number(red),
                        "goals_per_90": "" if per90(goals, minutes) is None else f"{per90(goals, minutes):.4f}",
                        "assists_per_90": "" if per90(assists, minutes) is None else f"{per90(assists, minutes):.4f}",
                        "goal_contributions_per_90": "" if assists is None or per90(goals + assists, minutes) is None else f"{per90(goals + assists, minutes):.4f}",
                        "discipline_events_per_90": "" if per90(discipline, minutes) is None else f"{per90(discipline, minutes):.4f}",
                    })
                elif rows:
                    base.update({"season_source": "TRANSFERMARKT_SUPPLEMENTAL_ONLY",
                                 "supplemental_competition_rows": str(len(rows)), "input_rows": str(len(rows))})
            if base["season_source"]:
                bases.append(base)

    if len(bases) != 981:
        raise RuntimeError(f"Expected 981 meaningful player-season bases, got {len(bases)}")
    write(OUT / "player-season-performance-base.csv", bases)

    minute_samples = [integer(row["minutes_played"]) for row in bases
                      if row["season_source"] == "TRANSFERMARKT" and integer(row["minutes_played"]) and integer(row["minutes_played"]) > 0]
    threshold = int(round(quantile(minute_samples, .25)))
    audit = []
    for role in ("ALL", "P", "D", "C", "A"):
        values = [integer(row["minutes_played"]) for row in bases if row["season_source"] == "TRANSFERMARKT"
                  and integer(row["minutes_played"]) and (role == "ALL" or row["current_role"] == role)]
        audit.append({"population": role, "samples": len(values), "min": min(values), "p10": round(quantile(values,.1),2),
                      "p25": round(quantile(values,.25),2), "median": round(quantile(values,.5),2),
                      "p75": round(quantile(values,.75),2), "p90": round(quantile(values,.9),2), "max": max(values),
                      "v1_small_sample_threshold": threshold if role == "ALL" else ""})
    write(OUT / "minutes-distribution-audit.csv", audit)

    by_player = defaultdict(list)
    for row in bases: by_player[row["player_id"]].append(row)
    profiles = []
    for player_id in sorted(context, key=int):
        rows = sorted(by_player[player_id], key=lambda r: r["season"])
        latest, previous = rows[-1], rows[-2] if len(rows) == 2 else None
        latest_minutes = integer(latest["minutes_played"])
        small = latest["season_source"].startswith("TRANSFERMARKT") and (latest_minutes is None or latest_minutes < threshold)
        cov, ctx = coverage[player_id], context[player_id]
        fanta_count = sum(cov[f"has_fantacalcio_{s}"] == "YES" for s in ("2024_25", "2025_26"))
        source_mix = "TWO_FANTACALCIO" if fanta_count == 2 else ("MIXED" if fanta_count == 1 and len(rows) == 2 else
                     ("TWO_INTERNATIONAL" if fanta_count == 0 and len(rows) == 2 else "ONE_SEASON_ONLY"))
        goals, assists = integer(latest["goals"]), integer(latest["assists"])
        usage_known = latest["fantacalcio_voted_appearances"] != "" or latest["minutes_played"] != ""
        production_completeness = 100 if goals is not None and assists is not None else (50 if goals is not None or assists is not None else 0)
        season_component = len(rows) * 50
        usage_component = 100 if usage_known else 0
        fantasy_component = 100 if any(r["season_source"] == "FANTACALCIO" for r in rows) else 0
        confidence = round((season_component + usage_component + production_completeness + fantasy_component) / 4, 2)
        values = {
            "latest_source": latest["season_source"], "previous_source": previous["season_source"] if previous else None,
            "minutes_latest": latest_minutes, "minutes_previous": integer(previous["minutes_played"]) if previous else None,
            "fantacalcio_voted_appearances_latest": integer(latest["fantacalcio_voted_appearances"]),
            "fantacalcio_voted_appearances_previous": integer(previous["fantacalcio_voted_appearances"]) if previous else None,
            "transfermarkt_appearances_latest": integer(latest["transfermarkt_appearances"]),
            "transfermarkt_appearances_previous": integer(previous["transfermarkt_appearances"]) if previous else None,
            "goals_latest": goals, "goals_previous": integer(previous["goals"]) if previous else None,
            "assists_latest": assists, "assists_previous": integer(previous["assists"]) if previous else None,
            "goals_per_90_latest": decimal(latest["goals_per_90"]), "goals_per_90_previous": decimal(previous["goals_per_90"]) if previous else None,
            "assists_per_90_latest": decimal(latest["assists_per_90"]), "assists_per_90_previous": decimal(previous["assists_per_90"]) if previous else None,
            "goal_contributions_per_90_latest": decimal(latest["goal_contributions_per_90"]),
            "discipline_events_per_90_latest": decimal(latest["discipline_events_per_90"]),
            "average_rating_latest": decimal(latest["average_rating"]), "average_rating_previous": decimal(previous["average_rating"]) if previous else None,
            "fantasy_average_latest": decimal(latest["fantasy_average"]), "fantasy_average_previous": decimal(previous["fantasy_average"]) if previous else None,
            "goals_conceded_latest": integer(latest["goals_conceded"]), "penalties_saved_latest": integer(latest["penalties_saved"]),
            "delta_goals_per_90": None, "delta_assists_per_90": None, "delta_minutes": None,
            "fantasy_average_trend": None, "goal_rate_role_percentile": None,
            "assist_rate_role_percentile": None, "fantasy_average_role_percentile": None,
            "usage_role_percentile": None, "historical_performance_score": None,
            "cross_league_adjustment": None,
        }
        if previous and latest["season_source"] == previous["season_source"] == "TRANSFERMARKT":
            if values["goals_per_90_latest"] is not None and values["goals_per_90_previous"] is not None:
                values["delta_goals_per_90"] = round(values["goals_per_90_latest"] - values["goals_per_90_previous"],4)
            if values["assists_per_90_latest"] is not None and values["assists_per_90_previous"] is not None:
                values["delta_assists_per_90"] = round(values["assists_per_90_latest"] - values["assists_per_90_previous"],4)
            if values["minutes_latest"] is not None and values["minutes_previous"] is not None:
                values["delta_minutes"] = values["minutes_latest"] - values["minutes_previous"]
        if previous and latest["season_source"] == previous["season_source"] == "FANTACALCIO" and values["fantasy_average_latest"] is not None and values["fantasy_average_previous"] is not None:
            values["fantasy_average_trend"] = round(values["fantasy_average_latest"] - values["fantasy_average_previous"],2)
        evidence = {"season_coverage_component": season_component, "usage_evidence_component": usage_component,
                    "production_completeness_component": production_completeness, "fantasy_evidence_component": fantasy_component,
                    "small_sample_minutes_threshold": threshold, "overall_score_decision": "DEFERRED_TO_BLOCK_50"}
        fingerprint = hashlib.sha256(json.dumps({"version": VERSION, "rows": rows}, sort_keys=True, separators=(",",":")).encode()).hexdigest()
        profiles.append({
            "player_id": player_id, "player_name": ctx["player_name"], "current_role": ctx["current_role"],
            "current_club_id": ctx["current_club_id"], "current_club": ctx["current_club"],
            "reference_season": "2026/2027", "algorithm_version": VERSION,
            "history_seasons_available": len(rows), "latest_history_season": latest["season"], "source_mix": source_mix,
            "small_sample_flag": "YES" if small else "NO", "data_confidence_score": f"{confidence:.2f}",
            "input_fingerprint": fingerprint, "feature_values_json": json.dumps(values,sort_keys=True,separators=(",",":")),
            "evidence_json": json.dumps(evidence,sort_keys=True,separators=(",",":")), **{k:"" if v is None else v for k,v in values.items()},
        })

    # Role-relative percentiles. Missing and small-sample rates remain NULL.
    profile_by_id = {p["player_id"]: p for p in profiles}
    for role in ("P", "D", "C", "A"):
        role_profiles = [p for p in profiles if p["current_role"] == role]
        for field, out_field, eligibility in (
            ("goals_per_90_latest", "goal_rate_role_percentile", lambda p: role != "P" and p["small_sample_flag"] == "NO"),
            ("assists_per_90_latest", "assist_rate_role_percentile", lambda p: role != "P" and p["small_sample_flag"] == "NO"),
            ("fantasy_average_latest", "fantasy_average_role_percentile", lambda p: True),
        ):
            values = [(p["player_id"], decimal(str(p[field]))) for p in role_profiles if p[field] != "" and eligibility(p)]
            ranks = percentile_map(values)
            for player_id, rank in ranks.items(): profile_by_id[player_id][out_field] = rank
        tm_usage = [(p["player_id"], int(p["minutes_latest"])) for p in role_profiles if p["minutes_latest"] != "" and p["small_sample_flag"] == "NO"]
        fanta_usage = [(p["player_id"], int(p["fantacalcio_voted_appearances_latest"])) for p in role_profiles if p["fantacalcio_voted_appearances_latest"] != ""]
        for player_id, rank in {**percentile_map(tm_usage), **percentile_map(fanta_usage)}.items(): profile_by_id[player_id]["usage_role_percentile"] = rank

    # Refresh persisted JSON after percentile calculation.
    feature_keys = [key for key in json.loads(profiles[0]["feature_values_json"])]
    for profile in profiles:
        values = {key: (None if profile[key] == "" else profile[key]) for key in feature_keys}
        profile["feature_values_json"] = json.dumps(values,sort_keys=True,separators=(",",":"))
    write(OUT / "player-performance-features.csv", profiles)
    import_fields = ["player_id", "player_name", "current_role", "current_club_id", "current_club",
                     "reference_season", "algorithm_version", "history_seasons_available",
                     "latest_history_season", "source_mix", "small_sample_flag",
                     "data_confidence_score", "input_fingerprint", "feature_values_json", "evidence_json"]
    write(OUT / "player-performance-import.csv",
          [{key: profile[key] for key in import_fields} for profile in profiles], import_fields)
    if len(sys.argv) == 3 and sys.argv[1] == "--sandbox-context":
        sandbox_context = read(Path(sys.argv[2]))
        sandbox_by_identity = {(row["player_name"], row["current_role"], row["current_club"]): row
                               for row in sandbox_context}
        if len(sandbox_context) != 495 or len(sandbox_by_identity) != 495:
            raise RuntimeError("Sandbox context must contain 495 unique name/role/club identities")
        translated = []
        for profile in profiles:
            key = (profile["player_name"], profile["current_role"], profile["current_club"])
            target = sandbox_by_identity.get(key)
            if target is None:
                raise RuntimeError(f"Sandbox identity projection failed: {key}")
            row = {field: profile[field] for field in import_fields}
            row["player_id"] = target["player_id"]
            row["current_club_id"] = target["current_club_id"]
            row["input_fingerprint"] = hashlib.sha256(
                f"{profile['input_fingerprint']}|sandbox-player-id:{target['player_id']}".encode()
            ).hexdigest()
            translated.append(row)
        write(OUT / "player-performance-import-sandbox.csv", translated, import_fields)

    definitions = [
        ("fantacalcio_voted_appearances_latest","SOURCE_SPECIFIC","count","Fantacalcio PV; not physical appearances"),
        ("transfermarkt_appearances_latest","SOURCE_SPECIFIC","count","Domestic-league physical appearances"),
        ("minutes_latest","CROSS_SOURCE_UNAVAILABLE","minutes","Available only for Transfermarkt domestic league"),
        ("goals_latest","CROSS_SOURCE_SAFE","count","Goals in the selected season source"),
        ("assists_latest","CROSS_SOURCE_SAFE_WITH_NULL","count","NULL remains unknown"),
        ("goals_per_90_latest","TRANSFERMARKT_ONLY","rate_per_90","goals*90/minutes when minutes>0"),
        ("assists_per_90_latest","TRANSFERMARKT_ONLY","rate_per_90","assists*90/minutes; NULL assists stays NULL"),
        ("goal_contributions_per_90_latest","TRANSFERMARKT_ONLY","rate_per_90","NULL unless goals, assists and minutes are known"),
        ("average_rating_latest","FANTACALCIO_ONLY","rating","Never derived for international data"),
        ("fantasy_average_latest","FANTACALCIO_ONLY","rating","Never derived for international data"),
        ("goals_conceded_latest","FANTACALCIO_GOALKEEPER","count","Goalkeeper-specific source metric"),
        ("penalties_saved_latest","FANTACALCIO_GOALKEEPER","count","Goalkeeper-specific source metric"),
        ("goal_rate_role_percentile","ROLE_RELATIVE","percentile_0_100","Role population; eligible minutes only; goalkeeper NULL"),
        ("assist_rate_role_percentile","ROLE_RELATIVE","percentile_0_100","Role population; eligible minutes only; goalkeeper NULL"),
        ("fantasy_average_role_percentile","ROLE_RELATIVE","percentile_0_100","Role population with known Fantacalcio value"),
        ("usage_role_percentile","ROLE_SOURCE_SPECIFIC","percentile_0_100","PV or minutes ranked separately within role/source semantics"),
        ("data_confidence_score","EVIDENCE_ONLY","score_0_100","Unweighted mean of season, usage, production completeness and fantasy evidence"),
        ("historical_performance_score","DEFERRED","score_0_100","OVERALL_SCORE_DEFERRED_TO_BLOCK_50"),
        ("cross_league_adjustment","DEFERRED","factor","NULL; no arbitrary league coefficients"),
    ]
    write(OUT / "player-performance-feature-definitions.csv",
          [{"algorithm_version":VERSION,"feature_key":k,"semantic_class":c,"unit":u,"definition":d,"null_policy":"NULL_STAYS_NULL"} for k,c,u,d in definitions])

    metric_rows = [
        ("FANTACALCIO","appearances","YES","PV / partite a voto","NO","Not physical appearances"),
        ("FANTACALCIO","minutes_played","NO","Unavailable","NO","Never estimated"),
        ("FANTACALCIO","goals","YES","Season goals","YES","Count semantics compatible"),
        ("FANTACALCIO","assists","YES","Season assists","YES","Count semantics compatible"),
        ("FANTACALCIO","average_rating","YES","Fantacalcio media voto","NO","Source-specific"),
        ("FANTACALCIO","fantasy_average","YES","Fantacalcio fantamedia","NO","Source-specific"),
        ("FANTACALCIO","goals_conceded","YES","Fantacalcio goalkeeper goals conceded","NO","Goalkeeper/source-specific"),
        ("FANTACALCIO","penalties_saved","YES","Fantacalcio saved penalties","NO","Goalkeeper/source-specific"),
        ("FANTACALCIO","penalties_taken","YES","Fantacalcio season penalties taken","NO","Fantasy/source-specific"),
        ("FANTACALCIO","penalties_scored","YES","Fantacalcio season penalties scored","NO","Fantasy/source-specific"),
        ("FANTACALCIO","penalties_missed","YES","Fantacalcio season penalties missed","NO","Fantasy/source-specific"),
        ("FANTACALCIO","own_goals","YES","Fantacalcio season own goals","NO","Fantasy/source-specific"),
        ("FANTACALCIO","clean_sheets","PARTIAL","Fantacalcio clean sheets","NO","Source/goalkeeper-specific; NULL retained"),
        ("FANTACALCIO","saves","PARTIAL","Fantacalcio goalkeeper saves","NO","Source/goalkeeper-specific; NULL retained"),
        ("FANTACALCIO","yellow_cards","YES","Season yellow cards","YES","Count semantics compatible"),
        ("FANTACALCIO","red_cards","YES","Season red cards","YES","Does not expose second-yellow split"),
        ("FANTACALCIO","starts","NO","Unavailable","NO","Never estimated"),
        ("FANTACALCIO","expected_goals","NO","Unavailable","NO","NULL"),
        ("FANTACALCIO","expected_assists","NO","Unavailable","NO","NULL"),
        ("TRANSFERMARKT","appearances","YES","Physical competition appearances","NO","Not Fantacalcio PV"),
        ("TRANSFERMARKT","minutes_played","YES","Played minutes","NO","Fantacalcio minutes unavailable"),
        ("TRANSFERMARKT","goals","YES","Competition goals","YES","Domestic-league aggregation only"),
        ("TRANSFERMARKT","assists","PARTIAL","Competition assists","YES_WITH_NULL","Goalkeeper/missing rows remain NULL"),
        ("TRANSFERMARKT","yellow_cards","YES","Competition yellow cards","YES","Domestic-league aggregation only"),
        ("TRANSFERMARKT","second_yellow_cards","YES","Second-yellow dismissals","NO","No Fantacalcio equivalent column"),
        ("TRANSFERMARKT","red_cards","YES","Direct red cards","YES","Domestic-league aggregation only"),
        ("TRANSFERMARKT","starts","NO","Unavailable","NO","Never estimated"),
        ("TRANSFERMARKT","expected_goals","NO","Unavailable","NO","NULL"),
        ("TRANSFERMARKT","expected_assists","NO","Unavailable","NO","NULL"),
    ]
    write(OUT / "source-metric-availability.csv",
          [{"source":s,"metric":m,"available":a,"semantic_definition":d,"safe_for_cross_source_feature":safe,"notes":n} for s,m,a,d,safe,n in metric_rows])

    anomalies = []
    for p in profiles:
        if p["source_mix"] == "ONE_SEASON_ONLY": anomalies.append({"player_id":p["player_id"],"player_name":p["player_name"],"anomaly":"ONE_SEASON_ONLY","severity":"INFO","details":"Previous season remains NULL"})
        if p["small_sample_flag"] == "YES": anomalies.append({"player_id":p["player_id"],"player_name":p["player_name"],"anomaly":"SMALL_SAMPLE","severity":"INFO","details":f"Latest Transfermarkt minutes below {threshold} or domestic league unavailable"})
    write(OUT / "player-performance-anomalies.csv", anomalies, ["player_id","player_name","anomaly","severity","details"])

    selected = []
    for role in ("P","D","C","A"):
        selected += [p for p in profiles if p["current_role"] == role][:2]
    for mix in ("TWO_FANTACALCIO","MIXED","TWO_INTERNATIONAL","ONE_SEASON_ONLY"):
        candidate = next(p for p in profiles if p["source_mix"] == mix)
        if candidate not in selected: selected.append(candidate)
    golden_fields = ["player_id","player_name","current_role","current_club","source_mix","history_seasons_available",
                     "latest_history_season","minutes_latest","goals_latest","assists_latest","goals_per_90_latest",
                     "fantasy_average_latest","goal_rate_role_percentile","fantasy_average_role_percentile",
                     "data_confidence_score","small_sample_flag"]
    write(OUT / "golden-player-feature-audit.csv", [{key:p[key] for key in golden_fields} for p in selected], golden_fields)
    print(f"algorithm_version={VERSION} profiles={len(profiles)} season_bases={len(bases)} small_sample_threshold={threshold} anomalies={len(anomalies)}")


if __name__ == "__main__": main()
