-- ============================================================
-- V50 - AUCTION PRICE MODEL V1
-- Block 51
--
-- Versioned and auditable derived layer built on:
-- - B50 fantasy player value
-- - current Fantacalcio FVM market signal
--
-- B50 remains immutable.
-- Legacy auction_price_snapshots is preserved for compatibility.
-- ============================================================


-- ============================================================
-- 1. ALGORITHM REGISTRY
-- ============================================================

CREATE TABLE auction_price_algorithms (
    version VARCHAR(50) PRIMARY KEY,

    reference_season_id BIGINT NOT NULL
        REFERENCES seasons(id),

    b50_algorithm_version VARCHAR(50) NOT NULL
        REFERENCES player_value_algorithms(version),

    status VARCHAR(20) NOT NULL
        CHECK (
            status IN (
                'ACTIVE',
                'SUPERSEDED'
            )
        ),

    model_family VARCHAR(60) NOT NULL
        CHECK (
            model_family =
                'B50_MARKET_BLEND_AUCTION_PRICE'
        ),

    -- P: 55% B50 + 45% market signal
    goalkeeper_b50_weight NUMERIC(5,4) NOT NULL,

    -- D/C/A: 65% B50 + 35% market signal
    outfield_b50_weight NUMERIC(5,4) NOT NULL,

    -- Market signal:
    -- 50% FVM role percentile
    -- 50% logarithmic FVM strength
    market_percentile_weight NUMERIC(5,4) NOT NULL,
    market_strength_weight NUMERIC(5,4) NOT NULL,

    definition_payload JSONB NOT NULL,

    definition_fingerprint CHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ck_auction_algorithm_weights
        CHECK (
            goalkeeper_b50_weight
                BETWEEN 0 AND 1
            AND outfield_b50_weight
                BETWEEN 0 AND 1
            AND market_percentile_weight
                BETWEEN 0 AND 1
            AND market_strength_weight
                BETWEEN 0 AND 1

            AND ABS(
                market_percentile_weight
                + market_strength_weight
                - 1
            ) < 0.0001
        ),

    CONSTRAINT ck_auction_algorithm_payload
        CHECK (
            jsonb_typeof(definition_payload)
                = 'object'
        ),

    CONSTRAINT ck_auction_algorithm_fingerprint
        CHECK (
            definition_fingerprint
                ~ '^[0-9a-f]{64}$'
        )
);


-- ============================================================
-- 2. VERSIONED PLAYER PRICE PROFILES
-- ============================================================

CREATE TABLE auction_price_profiles (
    id BIGSERIAL PRIMARY KEY,

    player_id BIGINT NOT NULL
        REFERENCES players(id),

    reference_season_id BIGINT NOT NULL
        REFERENCES seasons(id),

    algorithm_version VARCHAR(50) NOT NULL
        REFERENCES auction_price_algorithms(version),

    b50_algorithm_version VARCHAR(50) NOT NULL
        REFERENCES player_value_algorithms(version),

    league_teams INTEGER NOT NULL,

    total_budget INTEGER NOT NULL,

    defense_modifier BOOLEAN NOT NULL,

    role_code CHAR(1) NOT NULL,

    -- --------------------------------------------------------
    -- B50 INPUT
    -- --------------------------------------------------------

    b50_role_rank INTEGER,

    b50_role_percentile NUMERIC(6,2),

    data_confidence_score NUMERIC(6,2) NOT NULL,

    -- --------------------------------------------------------
    -- CURRENT MARKET INPUT
    -- --------------------------------------------------------

    quotation NUMERIC(10,2),

    fantasy_market_value NUMERIC(10,2),

    fvm_role_percentile NUMERIC(6,2),

    fvm_strength NUMERIC(6,2),

    market_signal NUMERIC(6,2),

    -- --------------------------------------------------------
    -- B51 DERIVED VALUE
    -- --------------------------------------------------------

    auction_score NUMERIC(6,2),

    auction_role_rank INTEGER,

    auction_role_percentile NUMERIC(6,2),

    pricing_status VARCHAR(30) NOT NULL,

    minimum_price INTEGER,

    recommended_price INTEGER,

    maximum_price INTEGER,

    budget_percentage NUMERIC(7,3),

    evidence JSONB NOT NULL,

    input_fingerprint CHAR(64) NOT NULL,

    generated_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    -- One immutable output for each auction configuration.
    CONSTRAINT uq_auction_price_profile
        UNIQUE (
            player_id,
            reference_season_id,
            algorithm_version,
            league_teams,
            total_budget,
            defense_modifier
        ),

    CONSTRAINT ck_auction_profile_teams
        CHECK (
            league_teams BETWEEN 6 AND 12
        ),

    CONSTRAINT ck_auction_profile_budget
        CHECK (
            total_budget BETWEEN 150 AND 1000
        ),

    CONSTRAINT ck_auction_profile_role
        CHECK (
            role_code IN (
                'P',
                'D',
                'C',
                'A'
            )
        ),

    CONSTRAINT ck_auction_profile_confidence
        CHECK (
            data_confidence_score
                BETWEEN 0 AND 100
        ),

    CONSTRAINT ck_auction_profile_fvm
        CHECK (
            quotation IS NULL
            OR quotation >= 0
        ),

    CONSTRAINT ck_auction_profile_fmv
        CHECK (
            fantasy_market_value IS NULL
            OR fantasy_market_value >= 0
        ),

    CONSTRAINT ck_auction_profile_percentiles
        CHECK (
            (
                b50_role_percentile IS NULL
                OR b50_role_percentile
                    BETWEEN 0 AND 100
            )
            AND (
                fvm_role_percentile IS NULL
                OR fvm_role_percentile
                    BETWEEN 0 AND 100
            )
            AND (
                fvm_strength IS NULL
                OR fvm_strength
                    BETWEEN 0 AND 100
            )
            AND (
                market_signal IS NULL
                OR market_signal
                    BETWEEN 0 AND 100
            )
            AND (
                auction_score IS NULL
                OR auction_score
                    BETWEEN 0 AND 100
            )
            AND (
                auction_role_percentile IS NULL
                OR auction_role_percentile
                    BETWEEN 0 AND 100
            )
        ),

    CONSTRAINT ck_auction_profile_status
        CHECK (
            pricing_status IN (
                'PRICED',
                'INSUFFICIENT_EVIDENCE'
            )
        ),

    -- A priced player must have the complete derived output.
    -- Insufficient evidence must never receive a fake price.
    CONSTRAINT ck_auction_profile_price_state
        CHECK (
            (
                pricing_status = 'PRICED'

                AND b50_role_rank IS NOT NULL
                AND b50_role_rank > 0

                AND b50_role_percentile IS NOT NULL

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
            )
            OR
            (
                pricing_status =
                    'INSUFFICIENT_EVIDENCE'

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
        ),

    CONSTRAINT ck_auction_profile_evidence
        CHECK (
            jsonb_typeof(evidence)
                = 'object'
        ),

    CONSTRAINT ck_auction_profile_fingerprint
        CHECK (
            input_fingerprint
                ~ '^[0-9a-f]{64}$'
        )
);


-- ============================================================
-- 3. INDEXES
-- ============================================================

CREATE INDEX idx_auction_price_profile_ranking
    ON auction_price_profiles (
        reference_season_id,
        algorithm_version,
        league_teams,
        total_budget,
        defense_modifier,
        role_code,
        auction_role_rank
    );


CREATE INDEX idx_auction_price_profile_player
    ON auction_price_profiles (
        player_id,
        reference_season_id,
        algorithm_version
    );


CREATE INDEX idx_auction_price_profile_price
    ON auction_price_profiles (
        reference_season_id,
        algorithm_version,
        league_teams,
        total_budget,
        defense_modifier,
        recommended_price DESC
    );


-- ============================================================
-- 4. IMPORTANT SEMANTIC GUARANTEE
--
-- NULL FVM:
--     missing market evidence.
--
-- FVM = 0:
--     real market evidence with minimum market strength.
--
-- They MUST NOT be treated as equivalent by Block 51 code.
-- ============================================================
