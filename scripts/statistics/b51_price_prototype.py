import csv
import math
from pathlib import Path

RANKING_INPUT = Path(
    "data/statistics/value-model/player-fantasy-value-ranking.csv"
)

MARKET_INPUT = Path(
    "data/statistics/value-model/b51-fvm-context.csv"
)

OUTPUT = Path(
    "data/statistics/value-model/b51-price-prototype-audit.csv"
)

BUDGET = 1000
TEAMS = 8
DEFENSE_MODIFIER = False

ROLE_BLEND_WEIGHTS = {
    "P": {
        "b50": 0.55,
        "market": 0.45,
    },
    "D": {
        "b50": 0.65,
        "market": 0.35,
    },
    "C": {
        "b50": 0.65,
        "market": 0.35,
    },
    "A": {
        "b50": 0.65,
        "market": 0.35,
    },
}

FVM_PERCENTILE_WEIGHT = 0.50
FVM_STRENGTH_WEIGHT = 0.50

ROLE_ALLOCATIONS = {
    False: {
        "P": 0.06,
        "D": 0.10,
        "C": 0.25,
        "A": 0.59,
    },
    True: {
        "P": 0.07,
        "D": 0.15,
        "C": 0.23,
        "A": 0.55,
    },
}

SLOTS = {
    "P": 3,
    "D": 8,
    "C": 8,
    "A": 6,
}

PRICE_ANCHORS = {
    "P": [
        (100, 70),
        (97, 58),
        (95, 50),
        (90, 38),
        (80, 22),
        (70, 12),
        (50, 5),
        (30, 2),
        (0, 1),
    ],
    "D": [
        (100, 180),
        (98, 145),
        (95, 105),
        (90, 65),
        (80, 30),
        (70, 15),
        (50, 6),
        (30, 2),
        (0, 1),
    ],
    "C": [
        (100, 500),
        (98, 420),
        (95, 330),
        (90, 190),
        (85, 95),
        (80, 42),
        (70, 12),
        (60, 4),
        (50, 2),
        (0, 1),
    ],
    "A": [
        (100, 340),
        (98, 310),
        (95, 270),
        (90, 220),
        (80, 150),
        (70, 95),
        (50, 40),
        (30, 12),
        (0, 1),
    ],
}


def inclusive_percentiles(rows, value_key):
    """
    Percentile 0-100 dentro il ruolo.
    Valori uguali ricevono lo stesso percentile medio.
    """
    valid = [
        row for row in rows
        if row.get(value_key) not in (None, "")
    ]

    if not valid:
        return {}

    values = sorted(
        float(row[value_key])
        for row in valid
    )

    n = len(values)

    result = {}

    for row in valid:
        value = float(row[value_key])

        lower = sum(v < value for v in values)
        equal = sum(v == value for v in values)

        if n == 1:
            percentile = 100.0
        else:
            average_rank_zero_based = (
                lower + (equal - 1) / 2
            )

            percentile = (
                average_rank_zero_based
                / (n - 1)
                * 100.0
            )

        result[row["player_id"]] = percentile

    return result


def interpolate_price(role, percentile):
    anchors = PRICE_ANCHORS[role]

    if percentile >= anchors[0][0]:
        return float(anchors[0][1])

    if percentile <= anchors[-1][0]:
        return float(anchors[-1][1])

    for index in range(len(anchors) - 1):
        high_pct, high_price = anchors[index]
        low_pct, low_price = anchors[index + 1]

        if low_pct <= percentile <= high_pct:
            span = high_pct - low_pct

            if span == 0:
                return float(high_price)

            position = (
                percentile - low_pct
            ) / span

            return (
                low_price
                + position
                * (high_price - low_price)
            )

    return 1.0


def confidence_range(price, confidence, has_fvm):
    # Confidence NON modifica il recommended.
    # FVM mancante allarga solo il range.
    if confidence >= 90:
        low_factor, high_factor = 0.90, 1.10
    elif confidence >= 80:
        low_factor, high_factor = 0.85, 1.15
    elif confidence >= 70:
        low_factor, high_factor = 0.80, 1.20
    else:
        low_factor, high_factor = 0.72, 1.28

    if not has_fvm:
        low_factor -= 0.05
        high_factor += 0.05

    minimum = max(
        1,
        round(price * low_factor)
    )

    maximum = max(
        round(price),
        round(price * high_factor)
    )

    return minimum, maximum


with RANKING_INPUT.open(
    encoding="utf-8-sig",
    newline=""
) as handle:
    ranking_rows = list(
        csv.DictReader(handle)
    )

with MARKET_INPUT.open(
    encoding="utf-8-sig",
    newline=""
) as handle:
    market_rows = list(
        csv.DictReader(handle)
    )


market_by_player = {
    row["player_id"]: row
    for row in market_rows
}


ranked = [
    row
    for row in ranking_rows
    if row["ranking_status"] == "RANKED"
    and row["role_percentile"]
]


by_role = {
    "P": [],
    "D": [],
    "C": [],
    "A": [],
}

for row in ranked:
    enriched = dict(row)

    market = market_by_player.get(
        row["player_id"]
    )

    enriched["quotation"] = (
        market["quotation"]
        if market
        else ""
    )

    enriched["fantasy_market_value"] = (
        market["fantasy_market_value"]
        if market
        else ""
    )

    by_role[row["role"]].append(
        enriched
    )


output = []

for role, players in by_role.items():

    fvm_percentiles = inclusive_percentiles(
        players,
        "fantasy_market_value"
    )

    valid_fvms = [
        float(row["fantasy_market_value"])
        for row in players
        if row.get("fantasy_market_value")
        not in (None, "")
        and float(row["fantasy_market_value"]) >= 0
    ]

    maximum_role_fvm = (
        max(valid_fvms)
        if valid_fvms
        else None
    )

    scored = []

    for row in players:

        b50_percentile = float(
            row["role_percentile"]
        )

        fvm_percentile = (
            fvm_percentiles.get(
                row["player_id"]
            )
        )

        raw_fvm = (
            float(row["fantasy_market_value"])
            if row.get("fantasy_market_value")
            not in (None, "")
            else None
        )

        if (
            fvm_percentile is None
            or raw_fvm is None
            or maximum_role_fvm is None
        ):
            # NULL-aware:
            # nessun FVM valido non equivale a zero.
            market_signal = None
            auction_score = b50_percentile
            has_fvm = False

        else:
            # Forza assoluta FVM dentro il ruolo.
            #
            # log1p evita che pochi valori estremamente
            # alti schiaccino tutto il resto del mercato.
            fvm_strength = (
                math.log1p(raw_fvm)
                / math.log1p(maximum_role_fvm)
                * 100.0
            )

            market_signal = (
                fvm_percentile
                * FVM_PERCENTILE_WEIGHT
                + fvm_strength
                * FVM_STRENGTH_WEIGHT
            )

            blend = ROLE_BLEND_WEIGHTS[role]

            auction_score = (
                b50_percentile * blend["b50"]
                + market_signal * blend["market"]
            )

            has_fvm = True

        scored.append(
            (
                row,
                auction_score,
                fvm_percentile,
                market_signal,
                has_fvm,
            )
        )

    scored.sort(
        key=lambda item: (
            -item[1],
            int(item[0]["player_id"]),
        )
    )

    market_slots = (
        TEAMS * SLOTS[role]
    )

    contenders = scored[:market_slots]

    league_role_budget = (
        BUDGET
        * TEAMS
        * ROLE_ALLOCATIONS[
            DEFENSE_MODIFIER
        ][role]
    )

    budget_factor = (
        BUDGET / 1000.0
    )

    team_factor = (
        1.0
        + ((TEAMS - 8) * 0.05)
    )

    provisional = []

    for (
        row,
        auction_score,
        fvm_percentile,
        market_signal,
        has_fvm,
    ) in contenders:

        raw_price = interpolate_price(
            role,
            auction_score
        )

        raw_price *= budget_factor
        raw_price *= team_factor

        provisional.append(
            (
                row,
                auction_score,
                fvm_percentile,
                market_signal,
                has_fvm,
                max(1.0, raw_price),
            )
        )

    provisional_total = sum(
        item[5]
        for item in provisional
    )

    # Vincolo economico:
    # nessun ruolo può assorbire più del
    # budget aggregato disponibile nella lega.
    if provisional_total > league_role_budget:

        floors = len(provisional)

        distributable_budget = max(
            0,
            league_role_budget - floors
        )

        raw_extra = sum(
            max(0, item[5] - 1)
            for item in provisional
        )

        scale = (
            distributable_budget / raw_extra
            if raw_extra > 0
            else 0
        )

        provisional = [
            (
                row,
                auction_score,
                fvm_percentile,
                market_signal,
                has_fvm,
                1 + max(0, price - 1) * scale,
            )
            for (
                row,
                auction_score,
                fvm_percentile,
                market_signal,
                has_fvm,
                price,
            ) in provisional
        ]

    price_by_player = {
        item[0]["player_id"]: item
        for item in provisional
    }

    for (
        index,
        (
            row,
            auction_score,
            fvm_percentile,
            market_signal,
            has_fvm,
        ),
    ) in enumerate(
        scored,
        start=1
    ):

        stored = price_by_player.get(
            row["player_id"]
        )

        if stored:
            recommended_float = stored[5]
        else:
            recommended_float = 1.0

        recommended = max(
            1,
            round(recommended_float)
        )

        confidence = float(
            row["data_confidence_score"]
        )

        minimum, maximum = confidence_range(
            recommended_float,
            confidence,
            has_fvm
        )

        output.append({
            "player_id":
                row["player_id"],
            "player_name":
                row["player_name"],
            "club":
                row["club"],
            "role":
                role,
            "b50_player_value_score":
                row["player_value_score"],
            "b50_role_rank":
                row["role_rank"],
            "b50_role_percentile":
                row["role_percentile"],
            "quotation":
                row["quotation"],
            "fantasy_market_value":
                row["fantasy_market_value"],
            "fvm_role_percentile":
                (
                    f"{fvm_percentile:.2f}"
                    if fvm_percentile is not None
                    else ""
                ),
            "market_signal":
                (
                    f"{market_signal:.2f}"
                    if market_signal is not None
                    else ""
                ),
            "auction_score":
                f"{auction_score:.2f}",
            "auction_role_rank":
                index,
            "data_confidence_score":
                row["data_confidence_score"],
            "league_teams":
                TEAMS,
            "budget":
                BUDGET,
            "defense_modifier":
                str(
                    DEFENSE_MODIFIER
                ).upper(),
            "league_role_budget":
                round(
                    league_role_budget
                ),
            "minimum_price":
                minimum,
            "recommended_price":
                recommended,
            "maximum_price":
                maximum,
        })


OUTPUT.parent.mkdir(
    parents=True,
    exist_ok=True
)

with OUTPUT.open(
    "w",
    encoding="utf-8",
    newline=""
) as handle:

    writer = csv.DictWriter(
        handle,
        fieldnames=output[0].keys()
    )

    writer.writeheader()
    writer.writerows(output)


print(f"Creato: {OUTPUT}")
print()

for role in ("P", "D", "C", "A"):

    role_rows = [
        row
        for row in output
        if row["role"] == role
    ]

    role_rows.sort(
        key=lambda row:
            int(row["auction_role_rank"])
    )

    purchased = role_rows[
        : TEAMS * SLOTS[role]
    ]

    total = sum(
        row["recommended_price"]
        for row in purchased
    )

    role_budget = round(
        BUDGET
        * TEAMS
        * ROLE_ALLOCATIONS[
            DEFENSE_MODIFIER
        ][role]
    )

    print(
        f"=== {role} === "
        f"budget mercato={role_budget} | "
        f"somma prezzi={total}"
    )

    print(
        f"=== TOP 15 AUCTION {role} ==="
    )

    for row in role_rows[:15]:

        print(
            f'{int(row["auction_role_rank"]):>3} | '
            f'{row["player_name"]:<25} | '
            f'B50={float(row["b50_role_percentile"]):>6.2f} | '
            f'FVMp={float(row["fvm_role_percentile"] or 0):>6.2f} | '
            f'MKT={float(row["market_signal"] or 0):>6.2f} | '
            f'AUC={float(row["auction_score"]):>6.2f} | '
            f'FVM={float(row["fantasy_market_value"] or 0):>6.0f} | '
            f'price={row["recommended_price"]:>3} | '
            f'range={row["minimum_price"]}-{row["maximum_price"]}'
        )

    print()


print("=== CASI CONTROLLO ===")

targets = (
    "maignan",
    "stankovic f.",
    "dimarco",
    "mancini",
    "mctominay",
    "paz n.",
)

for row in output:

    name = row["player_name"].lower()

    if any(
        target in name
        for target in targets
    ):
        print(
            f'{row["player_name"]:<20} | '
            f'role={row["role"]} | '
            f'B50rank={row["b50_role_rank"]} | '
            f'AUCrank={row["auction_role_rank"]} | '
            f'FVM={row["fantasy_market_value"]} | '
            f'AUC={row["auction_score"]} | '
            f'price={row["recommended_price"]}'
        )






