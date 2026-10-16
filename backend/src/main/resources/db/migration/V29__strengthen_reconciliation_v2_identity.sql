-- ============================================================
-- RECONCILIATION V2
-- Source names are aliases, not unique player identifiers.
-- Stable source external IDs remain unique when available.
-- ============================================================

ALTER TABLE player_source_mappings
DROP CONSTRAINT IF EXISTS uq_source_external_player;

CREATE INDEX IF NOT EXISTS idx_source_mappings_source_name
ON player_source_mappings (
    source,
    external_name
);

CREATE INDEX IF NOT EXISTS idx_transfer_claim_official_lookup
ON player_transfer_claims (
    player_id,
    season_id,
    verification_status,
    verified_at DESC
);