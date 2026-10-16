-- ============================================================
-- B62.2B
-- Fantacalcio is the authoritative operational catalog.
--
-- Gazzetta and Fantamarkt are enrichment/statistical sources.
-- Their freshness must not block the operational Fantacalcio
-- catalog during the transfer market.
-- ============================================================

UPDATE data_source_freshness_policies
SET required_for_refresh = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE source IN (
    'GAZZETTA',
    'FANTAMARKT'
);

UPDATE data_source_freshness_policies
SET required_for_refresh = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE source = 'FANTACALCIO';