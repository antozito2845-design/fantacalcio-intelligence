package it.fantacalciointelligence.backend.auction;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class B67AuctionPricePersistenceService {

    private final JdbcClient jdbcClient;
    private final B67AuctionPriceModelService modelService;

    public B67AuctionPricePersistenceService(
            JdbcClient jdbcClient,
            B67AuctionPriceModelService modelService
    ) {
        this.jdbcClient = jdbcClient;
        this.modelService = modelService;
    }

    @Transactional
    public PersistResult generateAndPersist(
            String seasonCode,
            int budget,
            int teams,
            boolean defenseModifier
    ) {

        Long seasonId =
                jdbcClient.sql("""
                        SELECT id
                        FROM seasons
                        WHERE code = :seasonCode
                        """)
                        .param(
                                "seasonCode",
                                seasonCode
                        )
                        .query(Long.class)
                        .optional()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Stagione non trovata: "
                                                + seasonCode
                                )
                        );

        verifyB66Algorithm(seasonId);

        String definitionPayload =
                """
                {
                  "version":"B67_AUCTION_PRICE_V1",
                  "model_family":"B66_MARKET_ROLE_RELATIVE_AUCTION_PRICE",
                  "performance_version":"B66_FEATURES_V3",
                  "catalogue":"CURRENT_OPERATIONAL_FANTACALCIO",
                  "market_source":"FANTACALCIO",
                  "score_aggregation":"MEDIAN_AVAILABLE_ROLE_RELATIVE_SIGNALS",
                  "market_signals":[
                    "fvm_role_percentile",
                    "quotation_role_percentile"
                  ],
                  "outfield_performance_signals":[
                    "fantasy_average_role_percentile",
                    "usage_role_percentile",
                    "goal_rate_role_percentile",
                    "assist_rate_role_percentile"
                  ],
                  "goalkeeper_performance_signals":[
                    "fantasy_average_role_percentile",
                    "usage_role_percentile"
                  ],
                  "null_policy":"NULL_STAYS_NULL_AND_SIGNAL_OMITTED",
                  "zero_policy":"ZERO_IS_REAL_EVIDENCE",
                  "confidence_policy":"RANGE_AND_EVIDENCE_ONLY",
                  "no_history_policy":"MARKET_ONLY_PRICING_ALLOWED",
                  "price_curve":"INHERITED_FROM_B51",
                  "role_allocations_no_modifier":{
                    "P":0.06,
                    "D":0.10,
                    "C":0.25,
                    "A":0.59
                  },
                  "role_allocations_modifier":{
                    "P":0.07,
                    "D":0.15,
                    "C":0.23,
                    "A":0.55
                  }
                }
                """.replaceAll("\\s+", "");

        String definitionFingerprint =
                sha256(definitionPayload);

        registerAlgorithm(
                seasonId,
                definitionPayload,
                definitionFingerprint
        );

        List<B67AuctionPriceModelService.AuctionPriceModelResult>
                results =
                modelService.calculateAll(
                        seasonCode,
                        budget,
                        teams,
                        defenseModifier
                );

        int inserted = 0;
        int unchanged = 0;

        for (
                B67AuctionPriceModelService.AuctionPriceModelResult result :
                results
        ) {

            String evidence =
                    buildEvidenceJson(result);

            String inputFingerprint =
                    buildInputFingerprint(
                            seasonCode,
                            budget,
                            teams,
                            defenseModifier,
                            result
                    );

            ExistingProfile existing =
                    findExistingProfile(
                            result.playerId(),
                            seasonId,
                            budget,
                            teams,
                            defenseModifier
                    );

            if (existing != null) {

                if (
                        !existing.inputFingerprint()
                                .equals(inputFingerprint)
                ) {
                    throw new IllegalStateException(
                            "Conflitto profilo B67 immutabile per player_id="
                                    + result.playerId()
                    );
                }

                unchanged++;
                continue;
            }

            insertProfile(
                    result,
                    seasonId,
                    budget,
                    teams,
                    defenseModifier,
                    evidence,
                    inputFingerprint
            );

            inserted++;
        }

        return new PersistResult(
                B67AuctionPriceModelService.ALGORITHM_VERSION,
                results.size(),
                inserted,
                unchanged
        );
    }

    private void verifyB66Algorithm(
            Long seasonId
    ) {

        Integer count =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_performance_algorithms
                        WHERE version = :version
                          AND reference_season_id = :seasonId
                          AND status = 'ACTIVE'
                        """)
                        .param(
                                "version",
                                B67AuctionPriceModelService.PERFORMANCE_VERSION
                        )
                        .param(
                                "seasonId",
                                seasonId
                        )
                        .query(Integer.class)
                        .single();

        if (count != 1) {
            throw new IllegalStateException(
                    "B66_FEATURES_V3 ACTIVE richiesto per B67"
            );
        }
    }

    private void registerAlgorithm(
            Long seasonId,
            String payload,
            String fingerprint
    ) {

        Map<String, Object> existing =
                jdbcClient.sql("""
                        SELECT
                            reference_season_id,
                            performance_algorithm_version,
                            definition_fingerprint
                        FROM auction_price_algorithms
                        WHERE version = :version
                        """)
                        .param(
                                "version",
                                B67AuctionPriceModelService.ALGORITHM_VERSION
                        )
                        .query()
                        .listOfRows()
                        .stream()
                        .findFirst()
                        .orElse(null);

        if (existing != null) {

            Long existingSeasonId =
                    ((Number) existing.get(
                            "reference_season_id"
                    )).longValue();

            String existingPerformanceVersion =
                    existing.get(
                            "performance_algorithm_version"
                    ).toString();

            String existingFingerprint =
                    existing.get(
                            "definition_fingerprint"
                    ).toString();

            if (
                    !existingSeasonId.equals(seasonId)
                    || !B67AuctionPriceModelService.PERFORMANCE_VERSION
                            .equals(existingPerformanceVersion)
                    || !fingerprint.equals(
                            existingFingerprint
                    )
            ) {
                throw new IllegalStateException(
                        "Conflitto definizione algoritmo B67 immutabile"
                );
            }

            return;
        }

        jdbcClient.sql("""
                INSERT INTO auction_price_algorithms (
                    version,
                    reference_season_id,
                    b50_algorithm_version,
                    performance_algorithm_version,
                    status,
                    model_family,
                    goalkeeper_b50_weight,
                    outfield_b50_weight,
                    market_percentile_weight,
                    market_strength_weight,
                    definition_payload,
                    definition_fingerprint
                )
                VALUES (
                    :version,
                    :seasonId,
                    NULL,
                    :performanceVersion,
                    'CANDIDATE',
                    :modelFamily,
                    NULL,
                    NULL,
                    NULL,
                    NULL,
                    CAST(:payload AS jsonb),
                    :fingerprint
                )
                """)
                .param(
                        "version",
                        B67AuctionPriceModelService.ALGORITHM_VERSION
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "performanceVersion",
                        B67AuctionPriceModelService.PERFORMANCE_VERSION
                )
                .param(
                        "modelFamily",
                        B67AuctionPriceModelService.MODEL_FAMILY
                )
                .param(
                        "payload",
                        payload
                )
                .param(
                        "fingerprint",
                        fingerprint
                )
                .update();
    }

    private ExistingProfile findExistingProfile(
            Long playerId,
            Long seasonId,
            int budget,
            int teams,
            boolean defenseModifier
    ) {

        return jdbcClient.sql("""
                SELECT input_fingerprint
                FROM auction_price_profiles
                WHERE player_id = :playerId
                  AND reference_season_id = :seasonId
                  AND algorithm_version = :algorithmVersion
                  AND league_teams = :teams
                  AND total_budget = :budget
                  AND defense_modifier = :defenseModifier
                """)
                .param(
                        "playerId",
                        playerId
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "algorithmVersion",
                        B67AuctionPriceModelService.ALGORITHM_VERSION
                )
                .param(
                        "teams",
                        teams
                )
                .param(
                        "budget",
                        budget
                )
                .param(
                        "defenseModifier",
                        defenseModifier
                )
                .query(
                        (rs, rowNumber) ->
                                new ExistingProfile(
                                        rs.getString(
                                                "input_fingerprint"
                                        )
                                )
                )
                .optional()
                .orElse(null);
    }

    private void insertProfile(
            B67AuctionPriceModelService.AuctionPriceModelResult result,
            Long seasonId,
            int budget,
            int teams,
            boolean defenseModifier,
            String evidence,
            String inputFingerprint
    ) {

        jdbcClient.sql("""
                INSERT INTO auction_price_profiles (
                    player_id,
                    reference_season_id,
                    algorithm_version,
                    b50_algorithm_version,
                    performance_algorithm_version,
                    league_teams,
                    total_budget,
                    defense_modifier,
                    role_code,

                    b50_role_rank,
                    b50_role_percentile,

                    data_confidence_score,
                    quotation,
                    fantasy_market_value,

                    fvm_role_percentile,
                    quotation_role_percentile,
                    fvm_strength,
                    market_signal,

                    fantasy_average_role_percentile,
                    usage_role_percentile,
                    goal_rate_role_percentile,
                    assist_rate_role_percentile,

                    performance_signal_count,
                    evidence_tier,

                    auction_score,
                    auction_role_rank,
                    auction_role_percentile,

                    pricing_status,
                    minimum_price,
                    recommended_price,
                    maximum_price,
                    budget_percentage,

                    evidence,
                    input_fingerprint
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    :algorithmVersion,
                    NULL,
                    :performanceVersion,
                    :teams,
                    :budget,
                    :defenseModifier,
                    :role,

                    NULL,
                    NULL,

                    :confidence,
                    :quotation,
                    :fantasyMarketValue,

                    :fvmRolePercentile,
                    :quotationRolePercentile,
                    NULL,
                    :marketSignal,

                    :fantasyAverageRolePercentile,
                    :usageRolePercentile,
                    :goalRateRolePercentile,
                    :assistRateRolePercentile,

                    :performanceSignalCount,
                    :evidenceTier,

                    :auctionScore,
                    :auctionRoleRank,
                    :auctionRolePercentile,

                    :pricingStatus,
                    :minimumPrice,
                    :recommendedPrice,
                    :maximumPrice,
                    :budgetPercentage,

                    CAST(:evidence AS jsonb),
                    :inputFingerprint
                )
                """)
                .param(
                        "playerId",
                        result.playerId()
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "algorithmVersion",
                        B67AuctionPriceModelService.ALGORITHM_VERSION
                )
                .param(
                        "performanceVersion",
                        B67AuctionPriceModelService.PERFORMANCE_VERSION
                )
                .param(
                        "teams",
                        teams
                )
                .param(
                        "budget",
                        budget
                )
                .param(
                        "defenseModifier",
                        defenseModifier
                )
                .param(
                        "role",
                        result.role()
                )
                .param(
                        "confidence",
                        result.dataConfidenceScore()
                )
                .param(
                        "quotation",
                        result.quotation()
                )
                .param(
                        "fantasyMarketValue",
                        result.fantasyMarketValue()
                )
                .param(
                        "fvmRolePercentile",
                        result.fvmRolePercentile()
                )
                .param(
                        "quotationRolePercentile",
                        result.quotationRolePercentile()
                )
                .param(
                        "marketSignal",
                        result.marketSignal()
                )
                .param(
                        "fantasyAverageRolePercentile",
                        result.fantasyAverageRolePercentile()
                )
                .param(
                        "usageRolePercentile",
                        result.usageRolePercentile()
                )
                .param(
                        "goalRateRolePercentile",
                        result.goalRateRolePercentile()
                )
                .param(
                        "assistRateRolePercentile",
                        result.assistRateRolePercentile()
                )
                .param(
                        "performanceSignalCount",
                        result.performanceSignalCount()
                )
                .param(
                        "evidenceTier",
                        result.evidenceTier()
                )
                .param(
                        "auctionScore",
                        result.auctionScore()
                )
                .param(
                        "auctionRoleRank",
                        result.auctionRoleRank()
                )
                .param(
                        "auctionRolePercentile",
                        result.auctionRolePercentile()
                )
                .param(
                        "pricingStatus",
                        result.pricingStatus()
                )
                .param(
                        "minimumPrice",
                        result.minimumPrice()
                )
                .param(
                        "recommendedPrice",
                        result.recommendedPrice()
                )
                .param(
                        "maximumPrice",
                        result.maximumPrice()
                )
                .param(
                        "budgetPercentage",
                        result.budgetPercentage()
                )
                .param(
                        "evidence",
                        evidence
                )
                .param(
                        "inputFingerprint",
                        inputFingerprint
                )
                .update();
    }

    private String buildEvidenceJson(
            B67AuctionPriceModelService.AuctionPriceModelResult result
    ) {

        return """
                {
                  "performance_algorithm_version":"%s",
                  "pricing_algorithm_version":"%s",
                  "market_source":"FANTACALCIO",
                  "role":"%s",
                  "source_mix":"%s",
                  "evidence_tier":"%s",
                  "performance_signal_count":%d,
                  "pricing_status":"%s"
                }
                """.formatted(
                        B67AuctionPriceModelService.PERFORMANCE_VERSION,
                        B67AuctionPriceModelService.ALGORITHM_VERSION,
                        result.role(),
                        safeJson(result.sourceMix()),
                        result.evidenceTier(),
                        result.performanceSignalCount(),
                        result.pricingStatus()
                ).replaceAll("\\s+", "");
    }

    private String buildInputFingerprint(
            String seasonCode,
            int budget,
            int teams,
            boolean defenseModifier,
            B67AuctionPriceModelService.AuctionPriceModelResult result
    ) {

        String canonical =
                String.join(
                        "|",
                        B67AuctionPriceModelService.ALGORITHM_VERSION,
                        B67AuctionPriceModelService.PERFORMANCE_VERSION,
                        seasonCode,
                        Integer.toString(budget),
                        Integer.toString(teams),
                        Boolean.toString(defenseModifier),

                        safe(result.playerId()),
                        safe(result.role()),
                        safe(result.dataConfidenceScore()),
                        safe(result.historySeasonsAvailable()),
                        safe(result.sourceMix()),

                        safe(result.quotation()),
                        safe(result.fantasyMarketValue()),

                        safe(result.fvmRolePercentile()),
                        safe(result.quotationRolePercentile()),

                        safe(result.fantasyAverageRolePercentile()),
                        safe(result.usageRolePercentile()),
                        safe(result.goalRateRolePercentile()),
                        safe(result.assistRateRolePercentile()),

                        safe(result.marketSignal()),
                        safe(result.performanceSignalCount()),
                        safe(result.evidenceTier()),

                        safe(result.auctionScore()),
                        safe(result.auctionRoleRank()),
                        safe(result.auctionRolePercentile()),

                        safe(result.minimumPrice()),
                        safe(result.recommendedPrice()),
                        safe(result.maximumPrice())
                );

        return sha256(canonical);
    }

    private String safe(Object value) {
        return value == null
                ? "NULL"
                : value.toString();
    }

    private String safeJson(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String sha256(String value) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat
                    .of()
                    .formatHex(hash);

        } catch (
                NoSuchAlgorithmException exception
        ) {

            throw new IllegalStateException(
                    "SHA-256 non disponibile",
                    exception
            );
        }
    }

    private record ExistingProfile(
            String inputFingerprint
    ) {
    }

    public record PersistResult(
            String algorithmVersion,
            int calculatedProfiles,
            int insertedProfiles,
            int unchangedProfiles
    ) {
    }
}
