package it.fantacalciointelligence.backend.scouting;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class ForeignPlayerScoutingService {

    private final JdbcClient jdbcClient;

    public ForeignPlayerScoutingService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public ForeignPlayerScoutingResponse upsert(
            Long playerId,
            UpsertForeignPlayerScoutingRequest request
    ) {
        validatePlayerId(playerId);

        String seasonCode =
                normalizeRequired(
                        request.season(),
                        "La stagione è obbligatoria"
                );

        String previousClub =
                normalizeRequired(
                        request.previousClub(),
                        "Il club precedente è obbligatorio"
                );

        String previousLeague =
                normalizeRequired(
                        request.previousLeague(),
                        "Il campionato precedente è obbligatorio"
                );

        String previousCountryCode =
                normalizeCountryCode(
                        request.previousCountryCode()
                );

        String transferType =
                normalizeTransferType(
                        request.transferType()
                );

        PlayerSeasonReference reference =
                findRegisteredPlayer(
                        playerId,
                        seasonCode
                );

        String recommendation =
                calculateRecommendation(
                        request.adaptationScore(),
                        request.potentialScore(),
                        request.riskScore(),
                        request.confidenceScore()
                );

        jdbcClient.sql("""
                INSERT INTO foreign_player_scouting_reports (
                    player_id,
                    season_id,
                    previous_club,
                    previous_league,
                    previous_country_code,
                    transfer_type,
                    adaptation_score,
                    potential_score,
                    risk_score,
                    confidence_score,
                    recommendation,
                    scouting_summary
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    :previousClub,
                    :previousLeague,
                    :previousCountryCode,
                    :transferType,
                    :adaptationScore,
                    :potentialScore,
                    :riskScore,
                    :confidenceScore,
                    :recommendation,
                    :scoutingSummary
                )
                ON CONFLICT (
                    player_id,
                    season_id
                )
                DO UPDATE SET
                    previous_club =
                        EXCLUDED.previous_club,

                    previous_league =
                        EXCLUDED.previous_league,

                    previous_country_code =
                        EXCLUDED.previous_country_code,

                    transfer_type =
                        EXCLUDED.transfer_type,

                    adaptation_score =
                        EXCLUDED.adaptation_score,

                    potential_score =
                        EXCLUDED.potential_score,

                    risk_score =
                        EXCLUDED.risk_score,

                    confidence_score =
                        EXCLUDED.confidence_score,

                    recommendation =
                        EXCLUDED.recommendation,

                    scouting_summary =
                        EXCLUDED.scouting_summary,

                    updated_at =
                        CURRENT_TIMESTAMP
                """)
                .param("playerId", playerId)
                .param("seasonId", reference.seasonId())
                .param("previousClub", previousClub)
                .param("previousLeague", previousLeague)
                .param(
                        "previousCountryCode",
                        previousCountryCode
                )
                .param("transferType", transferType)
                .param(
                        "adaptationScore",
                        request.adaptationScore()
                )
                .param(
                        "potentialScore",
                        request.potentialScore()
                )
                .param("riskScore", request.riskScore())
                .param(
                        "confidenceScore",
                        request.confidenceScore()
                )
                .param(
                        "recommendation",
                        recommendation
                )
                .param(
                        "scoutingSummary",
                        request.scoutingSummary().trim()
                )
                .update();

        return findByPlayer(
                playerId,
                seasonCode
        );
    }

    @Transactional(readOnly = true)
    public List<ForeignPlayerScoutingResponse> findAll(
            String season,
            String recommendation
    ) {
        String seasonCode =
                normalizeRequired(
                        season,
                        "La stagione è obbligatoria"
                );

        String parsedRecommendation =
                recommendation == null
                        || recommendation.isBlank()
                        ? null
                        : normalizeRecommendation(
                                recommendation
                        );

        StringBuilder sql = new StringBuilder("""
                SELECT
                    report.player_id,
                    player.display_name,
                    club.name AS current_club,
                    role.fantasy_role,
                    season.code AS season_code,
                    report.previous_club,
                    report.previous_league,
                    report.previous_country_code,
                    report.transfer_type,
                    report.adaptation_score,
                    report.potential_score,
                    report.risk_score,
                    report.confidence_score,
                    report.recommendation,
                    report.scouting_summary,
                    report.updated_at

                FROM foreign_player_scouting_reports report

                JOIN players player
                    ON player.id = report.player_id

                JOIN seasons season
                    ON season.id = report.season_id

                JOIN player_registrations registration
                    ON registration.player_id =
                        report.player_id
                    AND registration.season_id =
                        report.season_id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN clubs club
                    ON club.id = registration.club_id

                LEFT JOIN player_roles role
                    ON role.player_id = report.player_id
                    AND role.season_id = report.season_id
                    AND role.source = 'FANTACALCIO'

                WHERE season.code = :season
                  AND player.active = TRUE
                """);

        if (parsedRecommendation != null) {
            sql.append("""
                    AND report.recommendation =
                        :recommendation
                    """);
        }

        sql.append("""
                ORDER BY
                    report.potential_score DESC,
                    report.adaptation_score DESC,
                    report.risk_score,
                    player.display_name
                """);

        var query =
                jdbcClient.sql(sql.toString())
                        .param("season", seasonCode);

        if (parsedRecommendation != null) {
            query = query.param(
                    "recommendation",
                    parsedRecommendation
            );
        }

        return query
                .query((resultSet, rowNumber) ->
                        mapResponse(resultSet)
                )
                .list();
    }

    @Transactional(readOnly = true)
    public ForeignPlayerScoutingResponse findByPlayer(
            Long playerId,
            String season
    ) {
        validatePlayerId(playerId);

        String seasonCode =
                normalizeRequired(
                        season,
                        "La stagione è obbligatoria"
                );

        return jdbcClient.sql("""
                SELECT
                    report.player_id,
                    player.display_name,
                    club.name AS current_club,
                    role.fantasy_role,
                    season.code AS season_code,
                    report.previous_club,
                    report.previous_league,
                    report.previous_country_code,
                    report.transfer_type,
                    report.adaptation_score,
                    report.potential_score,
                    report.risk_score,
                    report.confidence_score,
                    report.recommendation,
                    report.scouting_summary,
                    report.updated_at

                FROM foreign_player_scouting_reports report

                JOIN players player
                    ON player.id = report.player_id

                JOIN seasons season
                    ON season.id = report.season_id

                JOIN player_registrations registration
                    ON registration.player_id =
                        report.player_id
                    AND registration.season_id =
                        report.season_id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN clubs club
                    ON club.id = registration.club_id

                LEFT JOIN player_roles role
                    ON role.player_id = report.player_id
                    AND role.season_id = report.season_id
                    AND role.source = 'FANTACALCIO'

                WHERE report.player_id = :playerId
                  AND season.code = :season
                  AND player.active = TRUE
                """)
                .param("playerId", playerId)
                .param("season", seasonCode)
                .query((resultSet, rowNumber) ->
                        mapResponse(resultSet)
                )
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Scouting del giocatore non trovato"
                        )
                );
    }

    private PlayerSeasonReference findRegisteredPlayer(
            Long playerId,
            String season
    ) {
        return jdbcClient.sql("""
                SELECT
                    season.id AS season_id,
                    player.display_name

                FROM players player

                JOIN player_registrations registration
                    ON registration.player_id = player.id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN seasons season
                    ON season.id = registration.season_id

                WHERE player.id = :playerId
                  AND player.active = TRUE
                  AND season.code = :season
                """)
                .param("playerId", playerId)
                .param("season", season)
                .query((resultSet, rowNumber) ->
                        new PlayerSeasonReference(
                                resultSet.getLong(
                                        "season_id"
                                ),
                                resultSet.getString(
                                        "display_name"
                                )
                        )
                )
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Giocatore non registrato in Serie A "
                                        + "per la stagione indicata"
                        )
                );
    }

    private ForeignPlayerScoutingResponse mapResponse(
            java.sql.ResultSet resultSet
    ) throws java.sql.SQLException {
        OffsetDateTime updatedAt =
                resultSet.getObject(
                        "updated_at",
                        OffsetDateTime.class
                );

        return new ForeignPlayerScoutingResponse(
                resultSet.getLong("player_id"),
                resultSet.getString("display_name"),
                resultSet.getString("current_club"),
                resultSet.getString("fantasy_role"),
                resultSet.getString("season_code"),
                resultSet.getString("previous_club"),
                resultSet.getString("previous_league"),
                resultSet.getString(
                        "previous_country_code"
                ),
                resultSet.getString("transfer_type"),
                resultSet.getBigDecimal("adaptation_score"),
                resultSet.getBigDecimal("potential_score"),
                resultSet.getBigDecimal("risk_score"),
                resultSet.getBigDecimal("confidence_score"),
                resultSet.getString("recommendation"),
                resultSet.getString("scouting_summary"),
                updatedAt == null
                        ? null
                        : updatedAt.toInstant()
        );
    }

    private String calculateRecommendation(
            int adaptation,
            int potential,
            int risk,
            int confidence
    ) {
        BigDecimal score =
                BigDecimal.valueOf(adaptation)
                        .multiply(
                                new BigDecimal("0.30")
                        )
                        .add(
                                BigDecimal.valueOf(potential)
                                        .multiply(
                                                new BigDecimal(
                                                        "0.35"
                                                )
                                        )
                        )
                        .add(
                                BigDecimal.valueOf(100 - risk)
                                        .multiply(
                                                new BigDecimal(
                                                        "0.20"
                                                )
                                        )
                        )
                        .add(
                                BigDecimal.valueOf(confidence)
                                        .multiply(
                                                new BigDecimal(
                                                        "0.15"
                                                )
                                        )
                        );

        if (score.compareTo(
                new BigDecimal("80")
        ) >= 0
                && risk <= 45
                && confidence >= 60) {
            return "HIGH_PRIORITY";
        }

        if (score.compareTo(
                new BigDecimal("67")
        ) >= 0
                && risk <= 60) {
            return "RECOMMENDED";
        }

        if (score.compareTo(
                new BigDecimal("50")
        ) >= 0) {
            return "MONITOR";
        }

        return "HIGH_RISK";
    }

    private String normalizeCountryCode(
            String countryCode
    ) {
        String normalized =
                normalizeRequired(
                        countryCode,
                        "Il paese di provenienza è obbligatorio"
                )
                        .toUpperCase(Locale.ROOT);

        if (!normalized.matches("[A-Z]{2}")) {
            throw new BadRequestException(
                    "Il codice paese deve contenere due lettere"
            );
        }

        if ("IT".equals(normalized)) {
            throw new BadRequestException(
                    "Lo scouting estero non può avere "
                            + "l'Italia come paese di provenienza"
            );
        }

        return normalized;
    }

    private String normalizeTransferType(
            String transferType
    ) {
        String normalized =
                normalizeRequired(
                        transferType,
                        "La tipologia di trasferimento è obbligatoria"
                )
                        .toUpperCase(Locale.ROOT);

        if (!List.of(
                "PERMANENT",
                "LOAN",
                "FREE_AGENT",
                "RETURN_FROM_LOAN"
        ).contains(normalized)) {
            throw new BadRequestException(
                    "Tipologia di trasferimento non valida: "
                            + transferType
            );
        }

        return normalized;
    }

    private String normalizeRecommendation(
            String recommendation
    ) {
        String normalized =
                recommendation.trim()
                        .toUpperCase(Locale.ROOT);

        if (!List.of(
                "HIGH_PRIORITY",
                "RECOMMENDED",
                "MONITOR",
                "HIGH_RISK"
        ).contains(normalized)) {
            throw new BadRequestException(
                    "Raccomandazione scouting non valida: "
                            + recommendation
            );
        }

        return normalized;
    }

    private String normalizeRequired(
            String value,
            String errorMessage
    ) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(errorMessage);
        }

        return value.trim();
    }

    private void validatePlayerId(Long playerId) {
        if (playerId == null || playerId <= 0) {
            throw new BadRequestException(
                    "L'identificativo del giocatore non è valido"
            );
        }
    }

    private record PlayerSeasonReference(
            Long seasonId,
            String playerName
    ) {
    }
}