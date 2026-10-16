# Identity enrichment (Block 46)

Identity enrichment is deliberately separate from player reconciliation and statistical links.
It never writes `player_source_mappings`, never changes `player_reconciliation_results.sources_count`,
and never lets FBref determine identity or fantasy role.

The reproducible input is the local 2026/27 catalog plus verified local aliases. Wikidata is queried
through its documented structured API by exact Wikipedia title in small batches with an identifying
User-Agent. No web-page scraping, fuzzy search crawling, CAPTCHA bypass, or rate-limit retry is used.
Wikidata entity IDs, URLs, confidence, observation/import timestamps, and minimal JSON evidence are
stored per field. The API returned HTTP 429 for the secondary country-code lookup; the pipeline
therefore preserved nationality as NULL and did not retry aggressively.

The second pass also uses the locally acquired 2026/27 Transfermarkt master (638 unique players,
20 teams) without any network access. A verified `FANTAMARKT` mapping in the form
`TRANSFERMARKT-<numeric-id>` is a deterministic stable anchor. Otherwise HIGH requires an exact
verified multi-token full name, coherent current club, and a unique master candidate, or an exact
verified alias plus independently known DOB, coherent club, and uniqueness. Surname plus club is
always REVIEW. The project name `FANTAMARKT` is intentionally retained in reconciliation; the new
`TRANSFERMARKT` provenance source belongs only to the identity profile.

`VERIFIED_COMPLETE` and `VERIFIED_PARTIAL` require HIGH confidence, a canonical full name, an exact
birth date, a stable Wikidata QID, and independent corroboration by the locally available FBref full
name and birth year. Exact title without that corroboration is `REVIEW_REQUIRED`; absent source data
is `DATA_SOURCE_MISSING`; an explicit local coverage classification may produce
`NO_SERIE_A_HISTORY_EXPECTED`. Partial dates such as `YYYY-00-00` are rejected.

The FBref matcher accepts HIGH only for an exact, unique enriched full name plus exact birth year.
The same FBref ID can propagate that anchor across seasons if no conflict exists. Current/historical
club, role, nationality, surname, fuzzy similarity, or an exact name alone are never sufficient.
Two FBref IDs converging on one player, or one FBref ID producing conflicting players, are REVIEW.

The separate identity readiness endpoint is `GET /api/v1/admin/identity/readiness?season=2026/2027`. Missing historical
coverage for a legitimate new arrival does not block the Catalog Quality Gate. CSV import is
idempotent and available at `POST /api/v1/admin/imports/identity/csv`; it updates only NULL player
fields from HIGH verified rows and rejects conflicting existing values atomically.
Migration V43 adds nationality text and the unique numeric Transfermarkt identity key and extends
allowed provenance fields/sources; V42 remains unchanged because it has already been exercised by Flyway.
