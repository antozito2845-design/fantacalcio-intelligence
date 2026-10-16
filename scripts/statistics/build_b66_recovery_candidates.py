from __future__ import annotations

import csv
import json
import time
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]

DATA = (
    ROOT
    / "data"
    / "statistics"
    / "international"
)

OUT = (
    DATA
    / "b66-no-history-recovery"
)

OUT.mkdir(
    parents=True,
    exist_ok=True,
)

REPORT = (
    ROOT
    / "backend"
    / "b66-recovery-candidates.txt"
)

CSV_OUT = (
    OUT
    / "b66-recovery-candidates.csv"
)

PLAYERS = [
    (1032, "Molina N.", "424042", "Nahuel Molina"),
    (1033, "Kevin Carlos", "718201", "Kevin Carlos"),
    (1034, "Romero D.", "668512", "David Romero"),
    (1035, "Schmid", "346853", "Romano Schmid"),
    (1036, "Grillitsch", "195736", "Florian Grillitsch"),
    (1037, "Terzic", "404260", "Aleksa Terzic"),
    (1038, "Aurelio", "457444", "Giuseppe Aurelio"),
    (1111, "Spence", "483348", "Djed Spence"),
]

TARGET_SEASONS = {
    2024: "2024/2025",
    2025: "2025/2026",
}

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/151 Safari/537.36"
    ),
    "Accept": "application/json",
    "Accept-Language": "en-US,en;q=0.9",
}


def get_json(url):
    request = urllib.request.Request(
        url,
        headers=HEADERS,
    )

    with urllib.request.urlopen(
        request,
        timeout=60,
    ) as response:
        payload = response.read()

    return json.loads(
        payload.decode("utf-8")
    )


def integer(value):
    if value is None:
        return None

    return int(value)


def load_existing_competition_metadata():
    path = (
        DATA
        / "transfermarkt-international-statistics.csv"
    )

    result = {}

    if not path.exists():
        return result

    with path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        for row in csv.DictReader(stream):
            competition_id = row[
                "competition_code"
            ]

            metadata = {
                "competition_name":
                    row["competition_name"],
                "competition_type":
                    row["competition_type"],
                "country":
                    row["country"],
                "country_code":
                    row["country_code"],
            }

            existing = result.get(
                competition_id
            )

            if existing is None:
                result[
                    competition_id
                ] = metadata
                continue

            # Il dataset storico puo contenere denominazioni
            # diverse della stessa competizione.
            # Il nome NON e un identificatore: competition_id lo e.
            #
            # Manteniamo quindi il primo nome non vuoto.
            if (
                not (
                    existing.get(
                        "competition_name"
                    )
                    or ""
                ).strip()
                and (
                    metadata.get(
                        "competition_name"
                    )
                    or ""
                ).strip()
            ):
                existing[
                    "competition_name"
                ] = metadata[
                    "competition_name"
                ]

            # Il tipo invece influenza direttamente B66:
            # DOMESTIC_LEAGUE deve essere distinto da coppe.
            #
            # Se lo storico contiene due tipi differenti,
            # NON scegliamo arbitrariamente: lasciamo vuoto
            # e il record finira in REVIEW.
            old_type = (
                existing.get(
                    "competition_type"
                )
                or ""
            ).strip()

            new_type = (
                metadata.get(
                    "competition_type"
                )
                or ""
            ).strip()

            if not old_type:
                existing[
                    "competition_type"
                ] = new_type

            elif (
                new_type
                and old_type != new_type
            ):
                existing[
                    "competition_type"
                ] = ""

            # Country/country_code non sono affidabili come
            # proprieta univoche per competizioni internazionali.
            # Se differiscono, lasciamo NULL/blank.
            for field in (
                "country",
                "country_code",
            ):
                old_value = (
                    existing.get(field)
                    or ""
                ).strip()

                new_value = (
                    metadata.get(field)
                    or ""
                ).strip()

                if not old_value:
                    existing[field] = new_value

                elif (
                    new_value
                    and old_value != new_value
                ):
                    existing[field] = ""

            result[
                competition_id
            ] = existing

    return result


def load_existing_clubs():
    path = (
        DATA
        / "transfermarkt-club-resolution.csv"
    )

    result = {}

    if not path.exists():
        return result

    with path.open(
        encoding="utf-8-sig",
        newline="",
    ) as stream:
        for row in csv.DictReader(stream):
            result[
                row["external_club_id"]
            ] = row

    return result


competition_metadata = (
    load_existing_competition_metadata()
)

existing_clubs = load_existing_clubs()

games_by_player = {}
all_competition_ids = set()


for (
    player_id,
    short_name,
    transfermarkt_id,
    full_name,
) in PLAYERS:

    print(
        f"RECOVERY API: {full_name}"
    )

    url = (
        "https://tmapi.transfermarkt.technology/"
        f"player/{transfermarkt_id}/performance-game"
    )

    payload = get_json(url)

    if not payload.get("success"):
        raise RuntimeError(
            f"Transfermarkt API failed for {full_name}"
        )

    games = payload.get(
        "data",
        {}
    ).get(
        "performance",
        []
    )

    filtered = []

    for game in games:
        info = game.get(
            "gameInformation",
            {}
        )

        season_id = integer(
            info.get("seasonId")
        )

        if season_id not in TARGET_SEASONS:
            continue

        # B66 storico club:
        # le partite delle nazionali NON devono entrare
        # nelle statistiche club.
        if info.get(
            "isNationalGame"
        ) is True:
            continue

        competition_id = str(
            info.get(
                "competitionId",
                "",
            )
        ).strip()

        if not competition_id:
            continue

        all_competition_ids.add(
            competition_id
        )

        filtered.append(game)

    games_by_player[
        str(player_id)
    ] = {
        "short_name": short_name,
        "full_name": full_name,
        "transfermarkt_id": transfermarkt_id,
        "games": filtered,
    }

    print(
        f"  club_games_24_25_25_26="
        f"{len(filtered)}"
    )

    time.sleep(1)


# ==========================================================
# Recupero metadata competizioni dall'endpoint TM già
# intercettato dal browser.
# ==========================================================

unknown_competitions = sorted(
    competition_id
    for competition_id in all_competition_ids
    if competition_id
    not in competition_metadata
)

if unknown_competitions:
    query = urllib.parse.urlencode(
        [
            ("ids[]", competition_id)
            for competition_id
            in unknown_competitions
        ]
    )

    url = (
        "https://tmapi.transfermarkt.technology/"
        "competitions?"
        + query
    )

    payload = get_json(url)

    for competition in payload.get(
        "data",
        []
    ):
        competition_id = str(
            competition["id"]
        )

        competition_metadata.setdefault(
            competition_id,
            {
                "competition_name":
                    competition.get(
                        "name",
                        "",
                    ),
                # Non inventiamo il tipo.
                # Verrà risolto sotto usando il
                # dataset certificato oppure REVIEW.
                "competition_type":
                    "",
                "country":
                    "",
                "country_code":
                    "",
            },
        )


# ==========================================================
# Aggregazione player / season / competition / club.
# ==========================================================

groups = defaultdict(
    lambda: {
        "appearances": 0,
        "starts": 0,
        "minutes": 0,
        "goals": 0,
        "assists": 0,
        "yellow": 0,
        "second_yellow": 0,
        "red": 0,
        "games": 0,
    }
)


for player_id, player in games_by_player.items():
    for game in player["games"]:

        info = game[
            "gameInformation"
        ]

        season_id = int(
            info["seasonId"]
        )

        season_code = TARGET_SEASONS[
            season_id
        ]

        competition_id = str(
            info["competitionId"]
        )

        club_info = (
            game.get(
                "clubsInformation",
                {}
            ).get(
                "club",
                {}
            )
        )

        club_id = str(
            club_info.get(
                "clubId",
                "",
            )
        )

        if not club_id:
            continue

        statistics = game.get(
            "statistics",
            {}
        )

        general = statistics.get(
            "generalStatistics",
            {}
        )

        goals_data = statistics.get(
            "goalStatistics",
            {}
        )

        cards = statistics.get(
            "cardStatistics",
            {}
        )

        playing = statistics.get(
            "playingTimeStatistics",
            {}
        )

        participation = general.get(
            "participationState"
        )

        if participation != "played":
            continue

        key = (
            player_id,
            season_code,
            competition_id,
            club_id,
        )

        row = groups[key]

        row["games"] += 1
        row["appearances"] += 1

        if playing.get(
            "isStarting"
        ) is True:
            row["starts"] += 1

        minutes = playing.get(
            "playedMinutes"
        )

        if minutes is not None:
            row["minutes"] += int(
                minutes
            )

        goals = goals_data.get(
            "goalsScoredTotalOfficial"
        )

        if goals is None:
            goals = goals_data.get(
                "goalsScoredTotal"
            )

        if goals is not None:
            row["goals"] += int(
                goals
            )

        assists = goals_data.get(
            "assistsOfficial"
        )

        if assists is None:
            assists = goals_data.get(
                "assists"
            )

        if assists is not None:
            row["assists"] += int(
                assists
            )

        yellow = cards.get(
            "yellowCardGross"
        )

        if yellow is None:
            yellow = cards.get(
                "yellowCardNet"
            )

        if yellow:
            row["yellow"] += int(
                yellow
            )

        # Se il feed partita non espone in maniera
        # distinguibile second-yellow/red, lasciamo
        # la classificazione finale da certificare.
        # Non inventiamo valori.


rows = []

for (
    player_id,
    season_code,
    competition_id,
    club_id,
), values in sorted(
    groups.items(),
    key=lambda item: (
        int(item[0][0]),
        item[0][1],
        item[0][2],
        int(item[0][3]),
    ),
):
    player = games_by_player[
        player_id
    ]

    competition = (
        competition_metadata.get(
            competition_id,
            {}
        )
    )

    club = existing_clubs.get(
        club_id
    )

    rows.append(
        {
            "player_id":
                player_id,
            "player_name":
                player["full_name"],
            "transfermarkt_id":
                player["transfermarkt_id"],
            "season_code":
                season_code,
            "competition_code":
                competition_id,
            "competition_name":
                competition.get(
                    "competition_name",
                    "",
                ),
            "competition_type":
                competition.get(
                    "competition_type",
                    "",
                ),
            "country":
                competition.get(
                    "country",
                    "",
                ),
            "country_code":
                competition.get(
                    "country_code",
                    "",
                ),
            "external_club_id":
                club_id,
            "club_name":
                (
                    club.get(
                        "club_name",
                        "",
                    )
                    if club
                    else ""
                ),
            "club_resolution_known":
                (
                    "YES"
                    if club
                    else "NO"
                ),
            "appearances":
                values["appearances"],
            "starts":
                values["starts"],
            "minutes_played":
                values["minutes"],
            "goals":
                values["goals"],
            "assists":
                values["assists"],
            "yellow_cards":
                values["yellow"],
            "second_yellow_cards":
                "",
            "red_cards":
                "",
            "game_rows":
                values["games"],
        }
    )


fields = [
    "player_id",
    "player_name",
    "transfermarkt_id",
    "season_code",
    "competition_code",
    "competition_name",
    "competition_type",
    "country",
    "country_code",
    "external_club_id",
    "club_name",
    "club_resolution_known",
    "appearances",
    "starts",
    "minutes_played",
    "goals",
    "assists",
    "yellow_cards",
    "second_yellow_cards",
    "red_cards",
    "game_rows",
]


with CSV_OUT.open(
    "w",
    encoding="utf-8",
    newline="",
) as stream:
    writer = csv.DictWriter(
        stream,
        fieldnames=fields,
        quoting=csv.QUOTE_ALL,
    )

    writer.writeheader()
    writer.writerows(rows)


report = []

report.append(
    "B66 - NO HISTORY RECOVERY CANDIDATES"
)

report.append(
    "PENEV=INTENTIONALLY_NO_HISTORY"
)

report.append("")

for player_id, player in games_by_player.items():
    player_rows = [
        row
        for row in rows
        if row["player_id"]
        == player_id
    ]

    report.append(
        "=" * 80
    )

    report.append(
        f"{player_id} | "
        f"{player['full_name']} | "
        f"TM {player['transfermarkt_id']}"
    )

    for season_code in (
        "2024/2025",
        "2025/2026",
    ):
        season_rows = [
            row
            for row in player_rows
            if row["season_code"]
            == season_code
        ]

        report.append(
            f"  {season_code}: "
            f"{len(season_rows)} "
            f"competition/club rows"
        )

        for row in season_rows:
            report.append(
                "    "
                f"{row['competition_code']} | "
                f"{row['competition_name']} | "
                f"type={row['competition_type'] or 'REVIEW'} | "
                f"clubId={row['external_club_id']} | "
                f"club={row['club_name'] or 'REVIEW'} | "
                f"apps={row['appearances']} | "
                f"starts={row['starts']} | "
                f"minutes={row['minutes_played']} | "
                f"goals={row['goals']} | "
                f"assists={row['assists']}"
            )

    report.append("")


unknown_type = sum(
    1
    for row in rows
    if not row[
        "competition_type"
    ]
)

unknown_club = sum(
    1
    for row in rows
    if row[
        "club_resolution_known"
    ] == "NO"
)

players_with_history = len(
    {
        row["player_id"]
        for row in rows
    }
)

report.append(
    "=" * 80
)

report.append(
    f"candidate_rows={len(rows)}"
)

report.append(
    f"players_with_history="
    f"{players_with_history}"
)

report.append(
    f"unknown_competition_type="
    f"{unknown_type}"
)

report.append(
    f"unknown_club_resolution="
    f"{unknown_club}"
)

REPORT.write_text(
    "\n".join(report),
    encoding="utf-8",
)

print()
print(
    "=== B66 RECOVERY CANDIDATES ==="
)

print(
    f"candidate_rows={len(rows)}"
)

print(
    f"players_with_history="
    f"{players_with_history}"
)

print(
    f"unknown_competition_type="
    f"{unknown_type}"
)

print(
    f"unknown_club_resolution="
    f"{unknown_club}"
)

print(
    f"csv={CSV_OUT}"
)

print(
    f"report={REPORT}"
)