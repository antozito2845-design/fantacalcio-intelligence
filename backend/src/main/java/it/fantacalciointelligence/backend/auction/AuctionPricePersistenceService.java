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
public class AuctionPricePersistenceService {

    private static final String MODEL_FAMILY =
            "B50_MARKET_BLEND_AUCTION_PRICE";

    private static final BigDecimal GOALKEEPER_B50_WEIGHT =
            new BigDecimal("0.5500");

    private static final BigDecimal OUTFIELD_B50_WEIGHT =
            new BigDecimal("0.6500");

    private static final BigDecimal MARKET_PERCENTILE_WEIGHT =
            new BigDecimal("0.5000");

    private static final BigDecimal MARKET_STRENGTH_WEIGHT =
            new BigDecimal("0.5000");

    private final JdbcClient jdbcClient;
    private final AuctionPriceModelService modelService;

    public AuctionPricePersistenceService(
            JdbcClient jdbcClient,
            AuctionPriceModelService modelService
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
                        .param("seasonCode", seasonCode)
                        .query(Long.class)
                        .optional()
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Stagione non trovata: "
                                                + seasonCode
                                )
                        );

        verifyB50Algorithm(seasonId);

        String definitionPayload =
                """
                {
                  "version":"B51_AUCTION_PRICE_V1",
                  "b50_version":"B50_FANTASY_VALUE_V1",
                  "goalkeeper_b50_weight":0.55,
                  "outfield_b50_weight":0.65,
                  "market_percentile_weight":0.50,
                  "market_strength_weight":0.50,
                  "fvm_strength":"log1p(fvm)/log1p(role_max_fvm)",
                  "null_fvm":"renormalize_to_b50",
                  "zero_fvm":"valid_minimum_market_signal",
                  "confidence":"range_only",
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

        List<AuctionPriceModelService.AuctionPriceModelResult>
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
                AuctionPriceModelService.AuctionPriceModelResult result :
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
                            "Conflitto profilo B51 immutabile per player_id="
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
                AuctionPriceModelService.ALGORITHM_VERSION,
                results.size(),
                inserted,
                unchanged
        );
    }

    private void verifyB50Algorithm(
            Long seasonId
    ) {
        Integer count =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM player_value_algorithms
                        WHERE version = :version
                          AND reference_season_id = :seasonId
                        """)
                        .param(
                                "version",
                                AuctionPriceModelService.B50_VERSION
                        )
                        .param(
                                "seasonId",
                                seasonId
                        )
                        .query(Integer.class)
                        .single();

        if (count != 1) {
            throw new IllegalStateException(
                    "Algoritmo B50 richiesto non disponibile"
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
                            b50_algorithm_version,
                            definition_fingerprint
                        FROM auction_price_algorithms
                        WHERE version = :version
                        """)
                        .param(
                                "version",
                                AuctionPriceModelService.ALGORITHM_VERSION
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

            String existingB50 =
                    existing.get(
                            "b50_algorithm_version"
                    ).toString();

            String existingFingerprint =
                    existing.get(
                            "definition_fingerprint"
                    ).toString();

            if (
                    !existingSeasonId.equals(seasonId)
                    || !AuctionPriceModelService.B50_VERSION
                            .equals(existingB50)
                    || !fingerprint.equals(
                            existingFingerprint
                    )
            ) {
                throw new IllegalStateException(
                        "Conflitto definizione algoritmo B51 immutabile"
                );
            }

            return;
        }

        jdbcClient.sql("""
                INSERT INTO auction_price_algorithms (
                    version,
                    reference_season_id,
                    b50_algorithm_version,
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
                    :b50Version,
                    'ACTIVE',
                    :modelFamily,
                    :goalkeeperWeight,
                    :outfieldWeight,
                    :marketPercentileWeight,
                    :marketStrengthWeight,
                    CAST(:payload AS jsonb),
                    :fingerprint
                )
                """)
                .param(
                        "version",
                        AuctionPriceModelService.ALGORITHM_VERSION
                )
                .param(
                        "seasonId",
                        seasonId
                )
                .param(
                        "b50Version",
                        AuctionPriceModelService.B50_VERSION
                )
                .param(
                        "modelFamily",
                        MODEL_FAMILY
                )
                .param(
                        "goalkeeperWeight",
                        GOALKEEPER_B50_WEIGHT
                )
                .param(
                        "outfieldWeight",
                        OUTFIELD_B50_WEIGHT
                )
                .param(
                        "marketPercentileWeight",
                        MARKET_PERCENTILE_WEIGHT
                )
                .param(
                        "marketStrengthWeight",
                        MARKET_STRENGTH_WEIGHT
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
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param(
                        "algorithmVersion",
                        AuctionPriceModelService.ALGORITHM_VERSION
                )
                .param("teams", teams)
                .param("budget", budget)
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
            AuctionPriceModelService.AuctionPriceModelResult result,
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
                    fvm_strength,
                    market_signal,
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
                    :b50Version,
                    :teams,
                    :budget,
                    :defenseModifier,
                    :role,
                    :b50RoleRank,
                    :b50RolePercentile,
                    :confidence,
                    :quotation,
                    :fantasyMarketValue,
                    :fvmRolePercentile,
                    :fvmStrength,
                    :marketSignal,
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
                        AuctionPriceModelService.ALGORITHM_VERSION
                )
                .param(
                        "b50Version",
                        AuctionPriceModelService.B50_VERSION
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
                        "b50RoleRank",
                        result.b50RoleRank()
                )
                .param(
                        "b50RolePercentile",
                        result.b50RolePercentile()
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
                        "fvmStrength",
                        result.fvmStrength()
                )
                .param(
                        "marketSignal",
                        result.marketSignal()
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
            AuctionPriceModelService.AuctionPriceModelResult result
    ) {
        return """
                {
                  "b50_algorithm_version":"%s",
                  "pricing_algorithm_version":"%s",
                  "market_source":"FANTACALCIO",
                  "role":"%s",
                  "pricing_status":"%s"
                }
                """.formatted(
                        AuctionPriceModelService.B50_VERSION,
                        AuctionPriceModelService.ALGORITHM_VERSION,
                        result.role(),
                        result.pricingStatus()
                ).replaceAll("\\s+", "");
    }

    private String buildInputFingerprint(
            String seasonCode,
            int budget,
            int teams,
            boolean defenseModifier,
            AuctionPriceModelService.AuctionPriceModelResult result
    ) {
        String canonical =
                String.join(
                        "|",
                        AuctionPriceModelService.ALGORITHM_VERSION,
                        AuctionPriceModelService.B50_VERSION,
                        seasonCode,
                        Integer.toString(budget),
                        Integer.toString(teams),
                        Boolean.toString(defenseModifier),
                        result.playerId().toString(),
                        safe(result.role()),
                        safe(result.b50RoleRank()),
                        safe(result.b50RolePercentile()),
                        safe(result.dataConfidenceScore()),
                        safe(result.quotation()),
                        safe(result.fantasyMarketValue()),
                        safe(result.fvmRolePercentile()),
                        safe(result.fvmStrength()),
                        safe(result.marketSignal()),
                        safe(result.auctionScore()),
                        safe(result.auctionRoleRank()),
                        safe(result.auctionRolePercentile()),
                        safe(result.pricingStatus()),
                        safe(result.minimumPrice()),
                        safe(result.recommendedPrice()),
                        safe(result.maximumPrice())
                );

        return sha256(canonical);
    }

    private String safe(
            Object value
    ) {
        return value == null
                ? "NULL"
                : value.toString();
    }

    private String sha256(
            String value
    ) {
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
