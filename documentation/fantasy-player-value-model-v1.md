# Fantasy Player Value Model V1

## Scope and boundaries

`B50_FANTASY_VALUE_V1` is a deterministic, rule-based historical fantasy-football value model for the 495 current operational 2026/2027 players. Its only input is the immutable `B49_FEATURES_V1` evidence layer for 2024/2025 and 2025/2026. It does not use age, market value, reputation, league factors, current form, prices, calendar or newly acquired data.

Fantacalcio is authoritative for fantasy average, rating, PV and goalkeeper evidence. Transfermarkt contributes only certified usage, production and discipline. Missing fantasy evidence for international players remains `NULL`, never zero and never an invented proxy.

## Components and role models

All available components are on 0–100 role-relative scales. Fantasy quality uses 80% Fantacalcio fantasy-average role percentile and 20% a B50-derived role percentile of average rating when both exist. Usage reuses the source-aware B49 usage percentile. Production uses the mean of available B49 goal/assist rate percentiles; Fantacalcio rows use source-and-role-relative latest raw goal/assist percentiles because minutes do not exist. The signals are combined once, avoiding raw/per-90 double counting within a player.

Consistency is inverse role percentile of absolute comparable-season change and exists only for two same-source seasons. Discipline is the inverse role percentile of certified Transfermarkt discipline events per 90 after B49 small-sample exclusion. Goalkeeper-specific value combines penalties-saved percentile (60%) and inverse goals-conceded-per-PV percentile (40%); raw goals conceded are never used. Offensive production is excluded for goalkeepers.

Base weights are:

- P: fantasy quality 45, usage 30, goalkeeper 20, consistency 5.
- D: fantasy quality 40, usage 30, production 15, consistency 10, discipline 5.
- C: fantasy quality 35, production 30, usage 20, consistency 10, discipline 5.
- A: production 40, fantasy quality 35, usage 15, consistency 7, discipline 3.

These deliberately simple weights follow the audited availability and the fantasy-first role priorities. Trend/consistency remains at most 10%.

## Null, confidence and ranking contract

`NULL` stays `NULL`. Available base weights are renormalized to sum to 1.0; at least two meaningful components are required. Otherwise the final score/rank/percentile is `NULL` and status is `INSUFFICIENT_EVIDENCE`. B49 confidence is copied unchanged and remains separate; it never multiplies value.

Ranks are per role only. Competition ranking is used for equal unrounded scores, with player ID providing deterministic display order but no semantic tie break. Role percentile is inclusive 0–100 with average ranks for ties. Component/final/percentile display precision is two decimals; contributions use four decimals, while calculations occur before rounding.

## Versioning, persistence and limitations

The database grain is player, reference season and algorithm version. Import is append-only and idempotent; changed output under the same key is a conflict. Formula changes require a new version. Source-mix distributions, component coverage/distributions, correlations, anomalies, golden cases and top/bottom ranks are published under `data/statistics/value-model`.

The principal limitation is structural source coverage: international-only players have no manufactured fantasy quality, Fantacalcio-only players have no minute-based discipline, and profiles with fewer than two valid components are explicitly unranked. This is evidence uncertainty, not low player value.
