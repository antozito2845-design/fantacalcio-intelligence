-- ============================================================
-- V67 - B67 AUCTION PRICE MODEL
-- ============================================================
--
-- B51 remains immutable historical evidence.
--
-- B67 uses:
-- - current operational Fantacalcio catalogue
-- - B66_FEATURES_V3
-- - Fantacalcio quotation
-- - Fantacalcio FVM
-- - role-relative formal B66 signals
-- - NULL_STAYS_NULL
-- - market-only pricing when history is unavailable
--
-- B67 is initially registered at runtime as CANDIDATE.
-- ============================================================


-- ============================================================
-- ALGORITHM REGISTRY
-- ============================================================

ALTER TABLE auction_price_algorithms
    DROP CONSTRAINT auction_price_algorithms_status_check;

ALTER TABLE auction_price_algorithms
    DROP CONSTRAINT auction_price_algorithms_model_family_check;


ALTER TABLE auction_price_algorithms
    ALTER COLUMN b50_algorithm_version DROP NOT NULL;

ALTER TABLE auction_price_algorithms
    ALTER COLUMN goalkeeper_b50_weight DROP NOT NULL;

ALTER TABLE auction_price_algorithms
    ALTER COLUMN outfield_b50_weight DROP NOT NULL;

ALTER TABLE auction_price_algorithms
    ALTER COLUMN market_percentile_weight DROP NOT NULL;

ALTER TABLE auction_price_algorithms
    ALTER COLUMN market_strength_weight DROP NOT NULL;


ALTER TABLE auction_price_algorithms
    ADD COLUMN performance_algorithm_version VARCHAR(50)
        REFERENCES player_performance_algorithms(version);


ALTER TABLE auction_price_algorithms
    ADD CONSTRAINT ck_auction_algorithm_status_v67
        CHECK (
            status IN (
                'CANDIDATE',
                'ACTIVE',
                'SUPERSEDED'
            )
        );


ALTER TABLE auction_price_algorithms
    ADD CONSTRAINT ck_auction_algorithm_model_family_v67
        CHECK (
            model_family IN (
                'B50_MARKET_BLEND_AUCTION_PRICE',
                'B66_MARKET_ROLE_RELATIVE_AUCTION_PRICE'
            )
        );


ALTER TABLE auction_price_algorithms
    ADD CONSTRAINT ck_auction_algorithm_dependency_v67
        CHECK (
            (
                model_family = 'B50_MARKET_BLEND_AUCTION_PRICE'

                AND b50_algorithm_version IS NOT NULL
                AND performance_algorithm_version IS NULL

                AND goalkeeper_b50_weight IS NOT NULL
                AND outfield_b50_weight IS NOT NULL
                AND market_percentile_weight IS NOT NULL
                AND market_strength_weight IS NOT NULL
            )
            OR
            (
                model_family = 'B66_MARKET_ROLE_RELATIVE_AUCTION_PRICE'

                AND b50_algorithm_version IS NULL
                AND performance_algorithm_version IS NOT NULL

                AND goalkeeper_b50_weight IS NULL
                AND outfield_b50_weight IS NULL
                AND market_percentile_weight IS NULL
                AND market_strength_weight IS NULL
            )
        );


-- ============================================================
-- PROFILE SCHEMA
-- ============================================================

ALTER TABLE auction_price_profiles
    ALTER COLUMN b50_algorithm_version DROP NOT NULL;


ALTER TABLE auction_price_profiles
    ADD COLUMN performance_algorithm_version VARCHAR(50)
        REFERENCES player_performance_algorithms(version);

ALTER TABLE auction_price_profiles
    ADD COLUMN quotation_role_percentile NUMERIC(6,2);

ALTER TABLE auction_price_profiles
    ADD COLUMN fantasy_average_role_percentile NUMERIC(6,2);

ALTER TABLE auction_price_profiles
    ADD COLUMN usage_role_percentile NUMERIC(6,2);

ALTER TABLE auction_price_profiles
    ADD COLUMN goal_rate_role_percentile NUMERIC(6,2);

ALTER TABLE auction_price_profiles
    ADD COLUMN assist_rate_role_percentile NUMERIC(6,2);

ALTER TABLE auction_price_profiles
    ADD COLUMN performance_signal_count INTEGER;

ALTER TABLE auction_price_profiles
    ADD COLUMN evidence_tier VARCHAR(30);


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_b67_quotation_role_percentile
        CHECK (
            quotation_role_percentile IS NULL
            OR quotation_role_percentile BETWEEN 0 AND 100
        );


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_b67_fantasy_average_role_percentile
        CHECK (
            fantasy_average_role_percentile IS NULL
            OR fantasy_average_role_percentile BETWEEN 0 AND 100
        );


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_b67_usage_role_percentile
        CHECK (
            usage_role_percentile IS NULL
            OR usage_role_percentile BETWEEN 0 AND 100
        );


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_b67_goal_rate_role_percentile
        CHECK (
            goal_rate_role_percentile IS NULL
            OR goal_rate_role_percentile BETWEEN 0 AND 100
        );


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_b67_assist_rate_role_percentile
        CHECK (
            assist_rate_role_percentile IS NULL
            OR assist_rate_role_percentile BETWEEN 0 AND 100
        );


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_b67_performance_signal_count
        CHECK (
            performance_signal_count IS NULL
            OR performance_signal_count BETWEEN 0 AND 4
        );


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_b67_evidence_tier
        CHECK (
            evidence_tier IS NULL
            OR evidence_tier IN (
                'HIGH_EVIDENCE',
                'MEDIUM_EVIDENCE',
                'LOW_EVIDENCE',
                'MARKET_ONLY'
            )
        );


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_auction_profile_dependency_v67
        CHECK (
            (
                b50_algorithm_version IS NOT NULL
                AND performance_algorithm_version IS NULL
            )
            OR
            (
                b50_algorithm_version IS NULL
                AND performance_algorithm_version IS NOT NULL
            )
        );


-- ============================================================
-- PRICE STATE
--
-- Preserve B51 semantics while allowing B67 to price players
-- without B50 evidence.
-- ============================================================

ALTER TABLE auction_price_profiles
    DROP CONSTRAINT ck_auction_profile_price_state;


ALTER TABLE auction_price_profiles
    ADD CONSTRAINT ck_auction_profile_price_state
        CHECK (
            (
                pricing_status = 'PRICED'

                AND auction_score IS NOT NULL

                AND auction_role_rank IS NOT NULL
                AND auction_role_rank > 0

                AND auction_role_percentile IS NOT NULL

                AND minimum_price IS NOT NULL
                AND minimum_price >= 1

                AND recommended_price IS NOT NULL
                AND recommended_price >= minimum_price

                AND maximum_price IS NOT NULL
                AND maximum_price >= recommended_price

                AND budget_percentage IS NOT NULL
                AND budget_percentage >= 0

                AND
                (
                    (
                        b50_algorithm_version IS NOT NULL
                        AND performance_algorithm_version IS NULL

                        AND b50_role_rank IS NOT NULL
                        AND b50_role_rank > 0

                        AND b50_role_percentile IS NOT NULL
                    )
                    OR
                    (
                        b50_algorithm_version IS NULL
                        AND performance_algorithm_version IS NOT NULL

                        AND b50_role_rank IS NULL
                        AND b50_role_percentile IS NULL

                        AND market_signal IS NOT NULL
                        AND evidence_tier IS NOT NULL
                    )
                )
            )
            OR
            (
                pricing_status = 'INSUFFICIENT_EVIDENCE'

                AND b50_role_rank IS NULL
                AND b50_role_percentile IS NULL

                AND auction_score IS NULL
                AND auction_role_rank IS NULL
                AND auction_role_percentile IS NULL

                AND minimum_price IS NULL
                AND recommended_price IS NULL
                AND maximum_price IS NULL
                AND budget_percentage IS NULL
            )
        );


-- ============================================================
-- SEMANTIC POLICY
-- ============================================================
--
-- NULL historical feature = UNKNOWN.
-- UNKNOWN is omitted, never converted to zero.
--
-- Zero is valid observed evidence.
--
-- Market domain:
-- median(FVM role percentile, quotation role percentile)
--
-- Performance domain:
-- median(available formal B66 role-relative signals)
--
-- Final B67 value:
-- equal combination of market and performance domains when both
-- exist; otherwise the available domain is used.
--
-- Confidence affects evidence tier / price range,
-- not player quality.
--
-- No B66 retrospective overall score is created.
-- ============================================================