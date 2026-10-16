# Player Performance Feature Model V1

## Scope and decision

`B49_FEATURES_V1` is an immutable, deterministic evidence layer for the 495 players with an `ACTIVE` 2026/2027 registration. It derives features only from the certified Block 48 Fantacalcio and Transfermarkt history for 2024/2025 and 2025/2026. It does not acquire data, change identity, roles, registrations, clubs or raw statistics.

The model deliberately publishes component features and a data-confidence score, not a performance ranking. `historical_performance_score` and `cross_league_adjustment` are always `NULL`; the recorded decision is `OVERALL_SCORE_DEFERRED_TO_BLOCK_50`. No source-quality or league-strength coefficients were invented.

## Season source selection

The logical grain is player × historical season. A meaningful Fantacalcio row is preferred because it is the certified Serie A source. Otherwise, Transfermarkt rows classified as `DOMESTIC_LEAGUE` are aggregated across clubs. Domestic cups, international cups, promotion/relegation/play-off competitions and all other rows remain supplemental evidence and never inflate the main season features.

Fantacalcio `appearances` means PV (partite a voto), not physical appearances. It is therefore stored as `fantacalcio_voted_appearances`; Transfermarkt appearances remain separate. Fantacalcio has no minutes, so minutes and per-90 features are never estimated for it.

## Null and aggregation policy

Unknown never becomes zero. Transfermarkt multi-club totals use strict-null aggregation: if any included domestic-league row has an unknown metric, the aggregate metric is `NULL`. Explicit zero remains zero. A per-90 value is emitted only when both its numerator and positive minutes are known:

`rate_per_90 = metric × 90 / minutes_played`

Trends are calculated only between comparable source semantics. Transfermarkt-to-Transfermarkt supports minutes and per-90 deltas. Fantacalcio-to-Fantacalcio supports fantasy-average trend. Mixed-source pairs deliberately have no trend.

## Roles and percentiles

Percentiles are calculated independently inside the current P/D/C/A role. They use inclusive 0–100 rank and average ranks for ties. Missing values stay `NULL`. Goalkeepers have no goal-rate or assist-rate percentile; their certified Fantacalcio rating, fantasy average, goals conceded and penalties saved remain available.

Usage is source-specific: Fantacalcio PV and Transfermarkt minutes are ranked separately and are never combined into one numeric population. Rate percentiles require an eligible Transfermarkt sample.

## Small-sample policy

The V1 threshold is empirical, not arbitrary: the overall 25th percentile of positive, aggregated Transfermarkt domestic-league minutes in the certified current-player history. The generated audit records 297 samples and a threshold of 1,057 minutes. Raw per-90 values remain visible below the threshold, but rate percentiles are `NULL` and `small_sample_flag` is true.

## Confidence score

`data_confidence_score` measures evidence completeness, never player quality. It is the unweighted mean of four transparent components:

- season coverage: 50 points per available historical season;
- latest usage evidence: 100 when Fantacalcio PV or Transfermarkt minutes are known, otherwise 0;
- latest production completeness: 100 when goals and assists are known, 50 when only one is known, otherwise 0;
- fantasy evidence: 100 when at least one Fantacalcio season exists, otherwise 0.

The score is constrained to 0–100 and its components are persisted in `evidence`. Performance features are not multiplied by confidence.

## Versioning and persistence

The database key is `(player_id, reference_season_id, algorithm_version)`. The certified importer is append-only: an identical rerun inserts nothing; the same version with changed definitions, fingerprint, context or JSON is rejected. A future algorithm must use a new version and may coexist with V1. Foreign keys, uniqueness, role/history/source bounds, confidence bounds, JSON object checks, SHA-256 shape and the deferred-score policy are enforced by migration V48.

The input fingerprint is SHA-256 over the algorithm version and canonical selected season rows. Generated timestamps are database metadata and are excluded from the deterministic artifacts.

## Reproduction

1. Export the certified current context with `scripts/statistics/export_player_performance_context.ps1`.
2. Run `python scripts/statistics/build_player_performance_features.py`.
3. Run `python -m unittest scripts/statistics/test_player_performance_features.py`.
4. Apply V48, then run the certified importer first with `-Preflight`, then twice without it.

Primary auditable artifacts live in `data/statistics/features/`: the season base, compact import, wide feature review, definition catalog, source availability matrix, minutes distribution, anomalies and golden-player audit.
