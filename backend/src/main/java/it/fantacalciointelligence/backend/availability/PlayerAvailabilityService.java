package it.fantacalciointelligence.backend.availability;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@Service
public class PlayerAvailabilityService {

    private final JdbcClient jdbcClient;

    public PlayerAvailabilityService(
            JdbcClient jdbcClient
    ) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional(readOnly = true)
    public List<PlayerAvailabilityResponse> findByMatchday(
            String season,
            int matchday,
            String status
    ) {
        validateSeason(season);
        validateMatchday(matchday);

        PlayerAvailabilityStatus parsedStatus =
                status == null || status.isBlank()
                        ? null
                        : PlayerAvailabilityStatus.fromValue(status);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    player.id AS player_id,
                    player.display_name,
                    club.name AS club_name,
                    season.code AS season_code,
                    availability.matchday,
                    availability.status,
                    availability.starting_probability,
                    availability.availability_probability,
                    availability.injury_description,
                    availability.expected_return_date,
                    availability.source,
                    availability.updated_at
                FROM player_availabilities availability
                JOIN players player
                    ON player.id = availability.player_id
                JOIN seasons season
                    ON season.id = availability.season_id
                JOIN player_registrations registration
                    ON registration.player_id = player.id
                    AND registration.season_id = season.id
                JOIN clubs club
                    ON club.id = registration.club_id
                WHERE season.code = :season
                  AND availability.matchday = :matchday
                """);

        if (parsedStatus != null) {
            sql.append("""
                    AND availability.status = :status
                    """);
        }

        sql.append("""
                ORDER BY
                    availability.starting_probability DESC NULLS LAST,
                    player.display_name
                """);

        var query = jdbcClient
                .sql(sql.toString())
                .param("season", season.trim())
                .param("matchday", matchday);

        if (parsedStatus != null) {
            query = query.param(
                    "status",
                    parsedStatus.name()
            );
        }

        return query
                .query((rs, rowNumber) ->
                        new PlayerAvailabilityResponse(
                                rs.getLong("player_id"),
                                rs.getString("display_name"),
                                rs.getString("club_name"),
                                rs.getString("season_code"),
                                rs.getInt("matchday"),
                                rs.getString("status"),
                                rs.getBigDecimal(
                                        "starting_probability"
                                ),
                                rs.getBigDecimal(
                                        "availability_probability"
                                ),
                                rs.getString(
                                        "injury_description"
                                ),
                                rs.getObject(
                                        "expected_return_date",
                                        LocalDate.class
                                ),
                                rs.getString("source"),
                                toInstant(
                                        rs.getObject(
                                                "updated_at",
                                                OffsetDateTime.class
                                        )
                                )
                        )
                )
                .list();
    }

    @Transactional(readOnly = true)
    public List<PlayerAvailabilityHistoryResponse> findHistory(
            Long playerId,
            String season,
            Integer matchday
    ) {
        validateSeason(season);

        if (matchday != null) {
            validateMatchday(matchday);
        }

        ensurePlayerExists(playerId);

        StringBuilder sql = new StringBuilder("""
                SELECT
                    history.id,
                    history.availability_id,
                    history.player_id,
                    player.display_name,
                    season.code AS season_code,
                    history.matchday,
                    history.status,
                    history.starting_probability,
                    history.availability_probability,
                    history.injury_description,
                    history.expected_return_date,
                    history.source,
                    history.changed_at
                FROM player_availability_history history
                JOIN players player
                    ON player.id = history.player_id
                JOIN seasons season
                    ON season.id = history.season_id
                WHERE history.player_id = :playerId
                  AND season.code = :season
                """);

        if (matchday != null) {
            sql.append("""
                    AND history.matchday = :matchday
                    """);
        }

        sql.append("""
                ORDER BY history.changed_at DESC, history.id DESC
                """);

        var query = jdbcClient
                .sql(sql.toString())
                .param("playerId", playerId)
                .param("season", season.trim());

        if (matchday != null) {
            query = query.param("matchday", matchday);
        }

        return query
                .query((rs, rowNumber) ->
                        new PlayerAvailabilityHistoryResponse(
                                rs.getLong("id"),
                                rs.getLong("availability_id"),
                                rs.getLong("player_id"),
                                rs.getString("display_name"),
                                rs.getString("season_code"),
                                rs.getInt("matchday"),
                                rs.getString("status"),
                                rs.getBigDecimal(
                                        "starting_probability"
                                ),
                                rs.getBigDecimal(
                                        "availability_probability"
                                ),
                                rs.getString(
                                        "injury_description"
                                ),
                                rs.getObject(
                                        "expected_return_date",
                                        LocalDate.class
                                ),
                                rs.getString("source"),
                                toInstant(
                                        rs.getObject(
                                                "changed_at",
                                                OffsetDateTime.class
                                        )
                                )
                        )
                )
                .list();
    }

    @Transactional
    public PlayerAvailabilityResponse update(
            Long playerId,
            UpdatePlayerAvailabilityRequest request
    ) {
        validateSeason(request.season());
        validateMatchday(request.matchday());
        ensurePlayerExists(playerId);

        PlayerAvailabilityStatus status =
                PlayerAvailabilityStatus.fromValue(
                        request.status()
                );

        validateStatusData(status, request);

        Long seasonId = jdbcClient.sql("""
                SELECT id
                FROM seasons
                WHERE code = :season
                """)
                .param("season", request.season().trim())
                .query(Long.class)
                .optional()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Stagione non trovata"
                        )
                );

        String source = normalizeSource(
                request.source()
        );

        BigDecimal startingProbability =
                normalizeProbability(
                        request.startingProbability(),
                        status,
                        true
                );

        BigDecimal availabilityProbability =
                normalizeProbability(
                        request.availabilityProbability(),
                        status,
                        false
                );

        String injuryDescription =
                normalizeNullableText(
                        request.injuryDescription()
                );

        Long availabilityId = jdbcClient.sql("""
                INSERT INTO player_availabilities (
                    player_id,
                    season_id,
                    matchday,
                    status,
                    starting_probability,
                    availability_probability,
                    injury_description,
                    expected_return_date,
                    source
                )
                VALUES (
                    :playerId,
                    :seasonId,
                    :matchday,
                    :status,
                    :startingProbability,
                    :availabilityProbability,
                    :injuryDescription,
                    :expectedReturnDate,
                    :source
                )
                ON CONFLICT (
                    player_id,
                    season_id,
                    matchday
                )
                DO UPDATE SET
                    status = EXCLUDED.status,
                    starting_probability =
                        EXCLUDED.starting_probability,
                    availability_probability =
                        EXCLUDED.availability_probability,
                    injury_description =
                        EXCLUDED.injury_description,
                    expected_return_date =
                        EXCLUDED.expected_return_date,
                    source = EXCLUDED.source,
                    updated_at = CURRENT_TIMESTAMP
                RETURNING id
                """)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("matchday", request.matchday())
                .param("status", status.name())
                .param(
                        "startingProbability",
                        startingProbability
                )
                .param(
                        "availabilityProbability",
                        availabilityProbability
                )
                .param(
                        "injuryDescription",
                        injuryDescription
                )
                .param(
                        "expectedReturnDate",
                        request.expectedReturnDate()
                )
                .param("source", source)
                .query(Long.class)
                .single();

        jdbcClient.sql("""
                INSERT INTO player_availability_history (
                    availability_id,
                    player_id,
                    season_id,
                    matchday,
                    status,
                    starting_probability,
                    availability_probability,
                    injury_description,
                    expected_return_date,
                    source
                )
                VALUES (
                    :availabilityId,
                    :playerId,
                    :seasonId,
                    :matchday,
                    :status,
                    :startingProbability,
                    :availabilityProbability,
                    :injuryDescription,
                    :expectedReturnDate,
                    :source
                )
                """)
                .param("availabilityId", availabilityId)
                .param("playerId", playerId)
                .param("seasonId", seasonId)
                .param("matchday", request.matchday())
                .param("status", status.name())
                .param(
                        "startingProbability",
                        startingProbability
                )
                .param(
                        "availabilityProbability",
                        availabilityProbability
                )
                .param(
                        "injuryDescription",
                        injuryDescription
                )
                .param(
                        "expectedReturnDate",
                        request.expectedReturnDate()
                )
                .param("source", source)
                .update();

        return findByMatchday(
                request.season(),
                request.matchday(),
                null
        )
                .stream()
                .filter(item ->
                        item.playerId().equals(playerId)
                )
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Disponibilità non trovata"
                        )
                );
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null
                ? null
                : value.toInstant();
    }

    private void ensurePlayerExists(Long playerId) {
        if (playerId == null || playerId <= 0) {
            throw new BadRequestException(
                    "L'identificativo del giocatore non è valido"
            );
        }

        boolean playerExists = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM players
                WHERE id = :playerId
                  AND active = TRUE
                """)
                .param("playerId", playerId)
                .query(Integer.class)
                .single() > 0;

        if (!playerExists) {
            throw new ResourceNotFoundException(
                    "Giocatore non trovato"
            );
        }
    }

    private void validateStatusData(
            PlayerAvailabilityStatus status,
            UpdatePlayerAvailabilityRequest request
    ) {
        if (status == PlayerAvailabilityStatus.INJURED
                && normalizeNullableText(
                        request.injuryDescription()
                ) == null) {

            throw new BadRequestException(
                    "La descrizione dell'infortunio è obbligatoria "
                            + "per un giocatore infortunato"
            );
        }

        if (status.preventsSelection()) {
            if (isPositive(request.startingProbability())
                    || isPositive(
                            request.availabilityProbability()
                    )) {

                throw new BadRequestException(
                        "Un giocatore indisponibile non può avere "
                                + "probabilità superiori a zero"
                );
            }
        }

        if (status == PlayerAvailabilityStatus.AVAILABLE
                && request.availabilityProbability() != null
                && request.availabilityProbability() < 50) {

            throw new BadRequestException(
                    "Un giocatore disponibile deve avere una "
                            + "probabilità di disponibilità almeno pari a 50"
            );
        }

        if (request.expectedReturnDate() != null
                && status != PlayerAvailabilityStatus.INJURED
                && status != PlayerAvailabilityStatus.UNAVAILABLE) {

            throw new BadRequestException(
                    "La data prevista di rientro è consentita solo "
                            + "per giocatori infortunati o indisponibili"
            );
        }
    }

    private BigDecimal normalizeProbability(
            Integer value,
            PlayerAvailabilityStatus status,
            boolean starting
    ) {
        if (status.preventsSelection()) {
            return BigDecimal.ZERO;
        }

        if (value != null) {
            return BigDecimal.valueOf(value);
        }

        if (!starting
                && status == PlayerAvailabilityStatus.AVAILABLE) {
            return BigDecimal.valueOf(100);
        }

        return null;
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            return "MANUAL";
        }

        return source.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeNullableText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private boolean isPositive(Integer value) {
        return value != null && value > 0;
    }

    private void validateSeason(String season) {
        if (season == null || season.isBlank()) {
            throw new BadRequestException(
                    "La stagione è obbligatoria"
            );
        }
    }

    private void validateMatchday(int matchday) {
        if (matchday < 1 || matchday > 38) {
            throw new BadRequestException(
                    "La giornata deve essere compresa tra 1 e 38"
            );
        }
    }
}