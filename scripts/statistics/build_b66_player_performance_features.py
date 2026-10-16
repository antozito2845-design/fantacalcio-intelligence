#!/usr/bin/env python3
"""Build B66 source-aware historical performance profiles for the live catalog."""

from __future__ import annotations

import csv
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "data" / "statistics"
OUT = DATA / "features"

VERSION = "B66_FEATURES_V3"
REFERENCE_SEASON = "2026/2027"
SEASONS = ("2024/2025", "2025/2026")
PLAYOFF_MARKERS = ("play-off", "playoff", "relegation", "promotion")


def read(path: Path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        return list(csv.DictReader(stream))


def write(path: Path, rows, fields):
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(
            stream,
            fieldnames=fields,
            quoting=csv.QUOTE_ALL,
        )
        writer.writeheader()
        writer.writerows(rows)


def integer(value):
    return None if value in (None, "") else int(value)


def decimal(value):
    return None if value in (None, "") else float(value)


def strict_sum(rows, field):
    values = [integer(row[field]) for row in rows]

    if not values or any(value is None for value in values):
        return None

    return sum(values)


def csv_number(value):
    return "" if value is None else str(value)


def is_domestic_league(row):
    return (
        row["competition_type"] == "DOMESTIC_LEAGUE"
        and not any(
            marker in row["competition_name"].lower()
            for marker in PLAYOFF_MARKERS
        )
    )


def aggregate_tm_league(rows):
    league = [
        row
        for row in rows
        if is_domestic_league(row)
    ]

    if not league:
        return None

    fields = (
        "appearances",
        "minutes_played",
        "goals",
        "assists",
        "yellow_cards",
        "second_yellow_cards",
        "red_cards",
    )

    result = {
        field: strict_sum(league, field)
        for field in fields
    }

    result["rows"] = league

    return result


def per90(value, minutes):
    if value is None or minutes is None or minutes <= 0:
        return None

    return round(value * 90.0 / minutes, 4)


def quantile(values, q):
    values = sorted(values)

    if not values:
        return None

    position = (len(values) - 1) * q
    lo = math.floor(position)
    hi = math.ceil(position)

    if lo == hi:
        return values[lo]

    return (
        values[lo]
        + (values[hi] - values[lo])
        * (position - lo)
    )


def percentile_map(items):
    by_value = defaultdict(list)

    for key, value in items:
        by_value[value].append(key)

    ordered = sorted(by_value)

    count = sum(
        len(by_value[value])
        for value in ordered
    )

    result = {}
    cursor = 0

    for value in ordered:
        size = len(by_value[value])
        average_index = cursor + (size - 1) / 2

        percentile = (
            50.0
            if count == 1
            else round(
                100 * average_index / (count - 1),
                2,
            )
        )

        for key in by_value[value]:
            result[key] = percentile

        cursor += size

    return result


def empty_feature_values():
    return {
        "latest_source": None,
        "previous_source": None,
        "minutes_latest": None,
        "minutes_previous": None,
        "fantacalcio_voted_appearances_latest": None,
        "fantacalcio_voted_appearances_previous": None,
        "transfermarkt_appearances_latest": None,
        "transfermarkt_appearances_previous": None,
        "goals_latest": None,
        "goals_previous": None,
        "assists_latest": None,
        "assists_previous": None,
        "goals_per_90_latest": None,
        "goals_per_90_previous": None,
        "assists_per_90_latest": None,
        "assists_per_90_previous": None,
        "goal_contributions_per_90_latest": None,
        "discipline_events_per_90_latest": None,
        "average_rating_latest": None,
        "average_rating_previous": None,
        "fantasy_average_latest": None,
        "fantasy_average_previous": None,
        "goals_conceded_latest": None,
        "penalties_saved_latest": None,
        "delta_goals_per_90": None,
        "delta_assists_per_90": None,
        "delta_minutes": None,
        "fantasy_average_trend": None,
        "goal_rate_role_percentile": None,
        "assist_rate_role_percentile": None,
        "fantasy_average_role_percentile": None,
        "usage_role_percentile": None,
        "historical_performance_score": None,
        "cross_league_adjustment": None,
    }


def main():
    context_rows = read(
        OUT / "current-player-feature-context.csv"
    )

    coverage_rows = read(
        DATA
        / "current-2026-2027-historical-coverage.csv"
    )

    context = {
        row["player_id"]: row
        for row in context_rows
    }

    coverage = {
        row["player_id"]: row
        for row in coverage_rows
    }

    if len(context) != len(context_rows):
        raise RuntimeError(
            "Duplicate player in current context"
        )

    if len(coverage) != len(coverage_rows):
        raise RuntimeError(
            "Duplicate player in historical coverage"
        )

    if set(context) != set(coverage):
        raise RuntimeError(
            "Current context/coverage population mismatch"
        )

    operational_count = len(context)

    if operational_count == 0:
        raise RuntimeError(
            "Operational population is empty"
        )

    fanta_by_id_season = {}

    for filename, season in (
        (
            "fantacalcio-serie-a-2024-25.csv",
            "2024/2025",
        ),
        (
            "fantacalcio-serie-a-2025-26.csv",
            "2025/2026",
        ),
    ):
        for row in read(
            DATA / "fantacalcio" / filename
        ):
            fanta_by_id_season[
                (row["external_id"], season)
            ] = row

    tm_groups = defaultdict(list)

    for row in read(
        DATA
        / "international"
        / "transfermarkt-international-statistics.csv"
    ):
        if row["player_id"] in context:
            tm_groups[
                (
                    row["player_id"],
                    row["season_code"],
                )
            ].append(row)

    bases = []

    for player_id in sorted(
        context,
        key=int,
    ):
        ctx = context[player_id]
        cov = coverage[player_id]

        for season, suffix in (
            ("2024/2025", "2024_25"),
            ("2025/2026", "2025_26"),
        ):
            base = {
                "player_id": player_id,
                "player_name": ctx["player_name"],
                "current_role": ctx["current_role"],
                "season": season,
                "season_source": "",
                "domestic_league_available": "NO",
                "domestic_competitions": "",
                "domestic_clubs": "",
                "supplemental_competition_rows": "0",
                "fantacalcio_voted_appearances": "",
                "transfermarkt_appearances": "",
                "minutes_played": "",
                "goals": "",
                "assists": "",
                "yellow_cards": "",
                "second_yellow_cards": "",
                "red_cards": "",
                "average_rating": "",
                "fantasy_average": "",
                "goals_conceded": "",
                "clean_sheets": "",
                "saves": "",
                "penalties_saved": "",
                "goals_per_90": "",
                "assists_per_90": "",
                "goal_contributions_per_90": "",
                "discipline_events_per_90": "",
                "input_rows": "0",
            }

            has_fanta = (
                cov[
                    f"has_fantacalcio_{suffix}"
                ]
                == "YES"
            )

            if has_fanta:
                fanta_id = cov["fantacalcio_id"]

                source = fanta_by_id_season.get(
                    (fanta_id, season)
                )

                if source is None:
                    raise RuntimeError(
                        "Missing Fantacalcio source row: "
                        f"{player_id} {season}"
                    )

                base.update(
                    {
                        "season_source": "FANTACALCIO",
                        "domestic_league_available": "YES",
                        "domestic_competitions": "Serie A",
                        "domestic_clubs": source["club"],
                        "input_rows": "1",
                        "fantacalcio_voted_appearances":
                            source["appearances"],
                        "goals": source["goals"],
                        "assists": source["assists"],
                        "yellow_cards":
                            source["yellow_cards"],
                        "red_cards":
                            source["red_cards"],
                        "average_rating":
                            source["average_rating"],
                        "fantasy_average":
                            source["fantasy_average"],
                        "goals_conceded":
                            source["goals_conceded"],
                        "clean_sheets":
                            source["clean_sheets"],
                        "saves":
                            source["saves"],
                        "penalties_saved":
                            source["penalties_saved"],
                    }
                )

            else:
                rows = tm_groups.get(
                    (player_id, season),
                    [],
                )

                aggregate = (
                    aggregate_tm_league(rows)
                )

                league = (
                    []
                    if aggregate is None
                    else aggregate["rows"]
                )

                supplemental = [
                    row
                    for row in rows
                    if row not in league
                ]

                if league:
                    minutes = aggregate[
                        "minutes_played"
                    ]

                    goals = aggregate["goals"]
                    assists = aggregate["assists"]

                    yellow = aggregate[
                        "yellow_cards"
                    ]

                    second_yellow = aggregate[
                        "second_yellow_cards"
                    ]

                    red = aggregate["red_cards"]

                    discipline = (
                        None
                        if any(
                            value is None
                            for value in (
                                yellow,
                                second_yellow,
                                red,
                            )
                        )
                        else (
                            yellow
                            + second_yellow
                            + red
                        )
                    )

                    goal_rate = per90(
                        goals,
                        minutes,
                    )

                    assist_rate = per90(
                        assists,
                        minutes,
                    )

                    contribution_rate = (
                        None
                        if assists is None
                        or goals is None
                        else per90(
                            goals + assists,
                            minutes,
                        )
                    )

                    discipline_rate = per90(
                        discipline,
                        minutes,
                    )

                    base.update(
                        {
                            "season_source":
                                "TRANSFERMARKT",
                            "domestic_league_available":
                                "YES",
                            "domestic_competitions":
                                "|".join(
                                    sorted(
                                        {
                                            row[
                                                "competition_name"
                                            ]
                                            for row in league
                                        }
                                    )
                                ),
                            "domestic_clubs":
                                "|".join(
                                    sorted(
                                        {
                                            row[
                                                "club_name"
                                            ]
                                            for row in league
                                        }
                                    )
                                ),
                            "supplemental_competition_rows":
                                str(
                                    len(supplemental)
                                ),
                            "input_rows":
                                str(len(rows)),
                            "transfermarkt_appearances":
                                csv_number(
                                    aggregate[
                                        "appearances"
                                    ]
                                ),
                            "minutes_played":
                                csv_number(minutes),
                            "goals":
                                csv_number(goals),
                            "assists":
                                csv_number(assists),
                            "yellow_cards":
                                csv_number(yellow),
                            "second_yellow_cards":
                                csv_number(
                                    second_yellow
                                ),
                            "red_cards":
                                csv_number(red),
                            "goals_per_90":
                                (
                                    ""
                                    if goal_rate is None
                                    else f"{goal_rate:.4f}"
                                ),
                            "assists_per_90":
                                (
                                    ""
                                    if assist_rate is None
                                    else f"{assist_rate:.4f}"
                                ),
                            "goal_contributions_per_90":
                                (
                                    ""
                                    if contribution_rate
                                    is None
                                    else
                                    f"{contribution_rate:.4f}"
                                ),
                            "discipline_events_per_90":
                                (
                                    ""
                                    if discipline_rate
                                    is None
                                    else
                                    f"{discipline_rate:.4f}"
                                ),
                        }
                    )

                elif rows:
                    base.update(
                        {
                            "season_source":
                                "TRANSFERMARKT_SUPPLEMENTAL_ONLY",
                            "supplemental_competition_rows":
                                str(len(rows)),
                            "input_rows":
                                str(len(rows)),
                        }
                    )

            if base["season_source"]:
                bases.append(base)

    base_fields = [
        "player_id",
        "player_name",
        "current_role",
        "season",
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

    write(
        OUT / "b66-player-season-performance-base.csv",
        bases,
        base_fields,
    )

    minute_samples = [
        integer(row["minutes_played"])
        for row in bases
        if row["season_source"]
        == "TRANSFERMARKT"
        and integer(
            row["minutes_played"]
        ) is not None
        and integer(
            row["minutes_played"]
        ) > 0
    ]

    if not minute_samples:
        raise RuntimeError(
            "No Transfermarkt minute sample available"
        )

    threshold = int(
        round(
            quantile(
                minute_samples,
                0.25,
            )
        )
    )

    by_player = defaultdict(list)

    for row in bases:
        by_player[
            row["player_id"]
        ].append(row)

    profiles = []

    for player_id in sorted(
        context,
        key=int,
    ):
        ctx = context[player_id]

        rows = sorted(
            by_player[player_id],
            key=lambda row: row["season"],
        )

        values = empty_feature_values()

        if not rows:
            source_mix = "NO_HISTORY"
            latest_history_season = ""
            small = True
            confidence = 0.0

            evidence = {
                "season_coverage_component": 0,
                "usage_evidence_component": 0,
                "production_completeness_component": 0,
                "fantasy_evidence_component": 0,
                "small_sample_minutes_threshold":
                    threshold,
                "overall_score_decision":
                    "NO_HISTORY",
            }

        else:
            latest = rows[-1]

            previous = (
                rows[-2]
                if len(rows) == 2
                else None
            )

            latest_minutes = integer(
                latest["minutes_played"]
            )

            small = (
                latest["season_source"].startswith(
                    "TRANSFERMARKT"
                )
                and (
                    latest_minutes is None
                    or latest_minutes < threshold
                )
            )

            cov = coverage[player_id]

            fanta_count = sum(
                cov[
                    f"has_fantacalcio_{suffix}"
                ]
                == "YES"
                for suffix in (
                    "2024_25",
                    "2025_26",
                )
            )

            if (
                fanta_count == 2
                and len(rows) == 2
            ):
                source_mix = (
                    "TWO_FANTACALCIO"
                )

            elif (
                fanta_count == 1
                and len(rows) == 2
            ):
                source_mix = "MIXED"

            elif (
                fanta_count == 0
                and len(rows) == 2
            ):
                source_mix = (
                    "TWO_INTERNATIONAL"
                )

            else:
                source_mix = (
                    "ONE_SEASON_ONLY"
                )

            goals = integer(
                latest["goals"]
            )

            assists = integer(
                latest["assists"]
            )

            usage_known = (
                latest[
                    "fantacalcio_voted_appearances"
                ]
                != ""
                or latest["minutes_played"]
                != ""
            )

            production_completeness = (
                100
                if goals is not None
                and assists is not None
                else (
                    50
                    if goals is not None
                    or assists is not None
                    else 0
                )
            )

            season_component = (
                len(rows) * 50
            )

            usage_component = (
                100
                if usage_known
                else 0
            )

            fantasy_component = (
                100
                if any(
                    row["season_source"]
                    == "FANTACALCIO"
                    for row in rows
                )
                else 0
            )

            confidence = round(
                (
                    season_component
                    + usage_component
                    + production_completeness
                    + fantasy_component
                )
                / 4,
                2,
            )

            values.update(
                {
                    "latest_source":
                        latest[
                            "season_source"
                        ],
                    "previous_source":
                        (
                            previous[
                                "season_source"
                            ]
                            if previous
                            else None
                        ),
                    "minutes_latest":
                        latest_minutes,
                    "minutes_previous":
                        (
                            integer(
                                previous[
                                    "minutes_played"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "fantacalcio_voted_appearances_latest":
                        integer(
                            latest[
                                "fantacalcio_voted_appearances"
                            ]
                        ),
                    "fantacalcio_voted_appearances_previous":
                        (
                            integer(
                                previous[
                                    "fantacalcio_voted_appearances"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "transfermarkt_appearances_latest":
                        integer(
                            latest[
                                "transfermarkt_appearances"
                            ]
                        ),
                    "transfermarkt_appearances_previous":
                        (
                            integer(
                                previous[
                                    "transfermarkt_appearances"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "goals_latest":
                        goals,
                    "goals_previous":
                        (
                            integer(
                                previous[
                                    "goals"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "assists_latest":
                        assists,
                    "assists_previous":
                        (
                            integer(
                                previous[
                                    "assists"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "goals_per_90_latest":
                        decimal(
                            latest[
                                "goals_per_90"
                            ]
                        ),
                    "goals_per_90_previous":
                        (
                            decimal(
                                previous[
                                    "goals_per_90"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "assists_per_90_latest":
                        decimal(
                            latest[
                                "assists_per_90"
                            ]
                        ),
                    "assists_per_90_previous":
                        (
                            decimal(
                                previous[
                                    "assists_per_90"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "goal_contributions_per_90_latest":
                        decimal(
                            latest[
                                "goal_contributions_per_90"
                            ]
                        ),
                    "discipline_events_per_90_latest":
                        decimal(
                            latest[
                                "discipline_events_per_90"
                            ]
                        ),
                    "average_rating_latest":
                        decimal(
                            latest[
                                "average_rating"
                            ]
                        ),
                    "average_rating_previous":
                        (
                            decimal(
                                previous[
                                    "average_rating"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "fantasy_average_latest":
                        decimal(
                            latest[
                                "fantasy_average"
                            ]
                        ),
                    "fantasy_average_previous":
                        (
                            decimal(
                                previous[
                                    "fantasy_average"
                                ]
                            )
                            if previous
                            else None
                        ),
                    "goals_conceded_latest":
                        integer(
                            latest[
                                "goals_conceded"
                            ]
                        ),
                    "penalties_saved_latest":
                        integer(
                            latest[
                                "penalties_saved"
                            ]
                        ),
                }
            )

            if (
                previous
                and latest[
                    "season_source"
                ]
                == "TRANSFERMARKT"
                and previous[
                    "season_source"
                ]
                == "TRANSFERMARKT"
            ):
                if (
                    values[
                        "goals_per_90_latest"
                    ]
                    is not None
                    and values[
                        "goals_per_90_previous"
                    ]
                    is not None
                ):
                    values[
                        "delta_goals_per_90"
                    ] = round(
                        values[
                            "goals_per_90_latest"
                        ]
                        - values[
                            "goals_per_90_previous"
                        ],
                        4,
                    )

                if (
                    values[
                        "assists_per_90_latest"
                    ]
                    is not None
                    and values[
                        "assists_per_90_previous"
                    ]
                    is not None
                ):
                    values[
                        "delta_assists_per_90"
                    ] = round(
                        values[
                            "assists_per_90_latest"
                        ]
                        - values[
                            "assists_per_90_previous"
                        ],
                        4,
                    )

                if (
                    values[
                        "minutes_latest"
                    ]
                    is not None
                    and values[
                        "minutes_previous"
                    ]
                    is not None
                ):
                    values[
                        "delta_minutes"
                    ] = (
                        values[
                            "minutes_latest"
                        ]
                        - values[
                            "minutes_previous"
                        ]
                    )

            if (
                previous
                and latest[
                    "season_source"
                ]
                == "FANTACALCIO"
                and previous[
                    "season_source"
                ]
                == "FANTACALCIO"
                and values[
                    "fantasy_average_latest"
                ]
                is not None
                and values[
                    "fantasy_average_previous"
                ]
                is not None
            ):
                values[
                    "fantasy_average_trend"
                ] = round(
                    values[
                        "fantasy_average_latest"
                    ]
                    - values[
                        "fantasy_average_previous"
                    ],
                    2,
                )

            latest_history_season = (
                latest["season"]
            )

            evidence = {
                "season_coverage_component":
                    season_component,
                "usage_evidence_component":
                    usage_component,
                "production_completeness_component":
                    production_completeness,
                "fantasy_evidence_component":
                    fantasy_component,
                "small_sample_minutes_threshold":
                    threshold,
                "overall_score_decision":
                    "DEFERRED_TO_BLOCK_50",
            }

        fingerprint_payload = {
            "version": VERSION,
            "player_id": player_id,
            "rows": rows,
            "source_mix": source_mix,
        }

        fingerprint = hashlib.sha256(
            json.dumps(
                fingerprint_payload,
                sort_keys=True,
                separators=(",", ":"),
            ).encode("utf-8")
        ).hexdigest()

        profiles.append(
            {
                "player_id":
                    player_id,
                "player_name":
                    ctx["player_name"],
                "current_role":
                    ctx["current_role"],
                "current_club_id":
                    ctx["current_club_id"],
                "current_club":
                    ctx["current_club"],
                "reference_season":
                    REFERENCE_SEASON,
                "algorithm_version":
                    VERSION,
                "history_seasons_available":
                    len(rows),
                "latest_history_season":
                    latest_history_season,
                "source_mix":
                    source_mix,
                "small_sample_flag":
                    (
                        "YES"
                        if small
                        else "NO"
                    ),
                "data_confidence_score":
                    f"{confidence:.2f}",
                "input_fingerprint":
                    fingerprint,
                "feature_values_json":
                    json.dumps(
                        values,
                        sort_keys=True,
                        separators=(",", ":"),
                    ),
                "evidence_json":
                    json.dumps(
                        evidence,
                        sort_keys=True,
                        separators=(",", ":"),
                    ),
                **{
                    key:
                        ""
                        if value is None
                        else value
                    for key, value
                    in values.items()
                },
            }
        )

    profile_by_id = {
        profile["player_id"]: profile
        for profile in profiles
    }

    for role in (
        "P",
        "D",
        "C",
        "A",
    ):
        role_profiles = [
            profile
            for profile in profiles
            if profile["current_role"]
            == role
        ]

        for (
            field,
            out_field,
            eligibility,
        ) in (
            (
                "goals_per_90_latest",
                "goal_rate_role_percentile",
                lambda profile:
                    role != "P"
                    and profile[
                        "small_sample_flag"
                    ] == "NO",
            ),
            (
                "assists_per_90_latest",
                "assist_rate_role_percentile",
                lambda profile:
                    role != "P"
                    and profile[
                        "small_sample_flag"
                    ] == "NO",
            ),
            (
                "fantasy_average_latest",
                "fantasy_average_role_percentile",
                lambda profile: True,
            ),
        ):
            ranking_values = [
                (
                    profile["player_id"],
                    decimal(
                        str(
                            profile[field]
                        )
                    ),
                )
                for profile in role_profiles
                if profile[field] != ""
                and eligibility(profile)
            ]

            ranks = percentile_map(
                ranking_values
            )

            for player_id, rank in (
                ranks.items()
            ):
                profile_by_id[
                    player_id
                ][out_field] = rank

        tm_usage = [
            (
                profile["player_id"],
                int(
                    profile[
                        "minutes_latest"
                    ]
                ),
            )
            for profile in role_profiles
            if profile[
                "minutes_latest"
            ] != ""
            and profile[
                "small_sample_flag"
            ] == "NO"
        ]

        fanta_usage = [
            (
                profile["player_id"],
                int(
                    profile[
                        "fantacalcio_voted_appearances_latest"
                    ]
                ),
            )
            for profile in role_profiles
            if profile[
                "fantacalcio_voted_appearances_latest"
            ] != ""
        ]

        combined = {
            **percentile_map(
                tm_usage
            ),
            **percentile_map(
                fanta_usage
            ),
        }

        for player_id, rank in (
            combined.items()
        ):
            profile_by_id[
                player_id
            ][
                "usage_role_percentile"
            ] = rank

    feature_keys = list(
        empty_feature_values()
    )

    for profile in profiles:
        values = {
            key: (
                None
                if profile[key] == ""
                else profile[key]
            )
            for key in feature_keys
        }

        profile[
            "feature_values_json"
        ] = json.dumps(
            values,
            sort_keys=True,
            separators=(",", ":"),
        )

    import_fields = [
        "player_id",
        "player_name",
        "current_role",
        "current_club_id",
        "current_club",
        "reference_season",
        "algorithm_version",
        "history_seasons_available",
        "latest_history_season",
        "source_mix",
        "small_sample_flag",
        "data_confidence_score",
        "input_fingerprint",
        "feature_values_json",
        "evidence_json",
    ]

    write(
        OUT / "b66-player-performance-import.csv",
        [
            {
                key: profile[key]
                for key in import_fields
            }
            for profile in profiles
        ],
        import_fields,
    )

    no_history = [
        profile
        for profile in profiles
        if profile["source_mix"]
        == "NO_HISTORY"
    ]

    if len(profiles) != operational_count:
        raise RuntimeError(
            "Generated profile population mismatch: "
            f"{len(profiles)} != {operational_count}"
        )

    if {
        profile["player_id"]
        for profile in profiles
    } != set(context):
        raise RuntimeError(
            "Generated profile identities do not "
            "match operational context"
        )

    if any(
        int(
            profile[
                "history_seasons_available"
            ]
        )
        not in (0, 1, 2)
        for profile in profiles
    ):
        raise RuntimeError(
            "Invalid historical season count"
        )

    print(
        f"algorithm_version={VERSION} "
        f"operational={operational_count} "
        f"profiles={len(profiles)} "
        f"season_bases={len(bases)} "
        f"no_history={len(no_history)} "
        f"small_sample_threshold={threshold}"
    )


if __name__ == "__main__":
    main()