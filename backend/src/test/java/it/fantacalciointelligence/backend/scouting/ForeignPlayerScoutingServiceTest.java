package it.fantacalciointelligence.backend.scouting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@SpringBootTest
@Transactional
class ForeignPlayerScoutingServiceTest {

    @Autowired
    private ForeignPlayerScoutingService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldCreateForeignPlayerScoutingReport() {
        Long playerId = findRegisteredPlayerId();

        ForeignPlayerScoutingResponse response =
                service.upsert(
                        playerId,
                        validRequest()
                );

        assertThat(response.playerId())
                .isEqualTo(playerId);

        assertThat(response.season())
                .isEqualTo("2026/2027");

        assertThat(response.previousClub())
                .isEqualTo("Ajax");

        assertThat(response.previousLeague())
                .isEqualTo("Eredivisie");

        assertThat(response.previousCountryCode())
                .isEqualTo("NL");

        assertThat(response.transferType())
                .isEqualTo("PERMANENT");

        assertThat(response.recommendation())
                .isEqualTo("HIGH_PRIORITY");

        assertThat(response.currentClub())
                .isNotBlank();

        assertThat(response.updatedAt())
                .isNotNull();

        Integer storedReports =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM foreign_player_scouting_reports
                        WHERE player_id = :playerId
                        """)
                        .param("playerId", playerId)
                        .query(Integer.class)
                        .single();

        assertThat(storedReports)
                .isEqualTo(1);
    }

    @Test
    void shouldUpdateExistingScoutingReport() {
        Long playerId = findRegisteredPlayerId();

        service.upsert(
                playerId,
                validRequest()
        );

        ForeignPlayerScoutingResponse updated =
                service.upsert(
                        playerId,
                        new UpsertForeignPlayerScoutingRequest(
                                "2026/2027",
                                "PSV Eindhoven",
                                "Eredivisie",
                                "NL",
                                "LOAN",
                                62,
                                70,
                                68,
                                72,
                                "Profilo interessante, ma con rischio "
                                        + "di adattamento elevato."
                        )
                );

        assertThat(updated.previousClub())
                .isEqualTo("PSV Eindhoven");

        assertThat(updated.transferType())
                .isEqualTo("LOAN");

        assertThat(updated.recommendation())
                .isEqualTo("MONITOR");

        Integer storedReports =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM foreign_player_scouting_reports
                        WHERE player_id = :playerId
                        """)
                        .param("playerId", playerId)
                        .query(Integer.class)
                        .single();

        assertThat(storedReports)
                .isEqualTo(1);
    }

    @Test
    void shouldFindReportsByRecommendation() {
        Long playerId = findRegisteredPlayerId();

        service.upsert(
                playerId,
                validRequest()
        );

        List<ForeignPlayerScoutingResponse> reports =
                service.findAll(
                        "2026/2027",
                        "HIGH_PRIORITY"
                );

        assertThat(reports)
                .anySatisfy(report -> {
                    assertThat(report.playerId())
                            .isEqualTo(playerId);

                    assertThat(report.recommendation())
                            .isEqualTo("HIGH_PRIORITY");
                });
    }

    @Test
    void shouldFindReportByPlayer() {
        Long playerId = findRegisteredPlayerId();

        service.upsert(
                playerId,
                validRequest()
        );

        ForeignPlayerScoutingResponse response =
                service.findByPlayer(
                        playerId,
                        "2026/2027"
                );

        assertThat(response.playerId())
                .isEqualTo(playerId);

        assertThat(response.scoutingSummary())
                .contains("adattamento");
    }

    @Test
    void shouldRejectItalianPreviousCountry() {
        Long playerId = findRegisteredPlayerId();

        UpsertForeignPlayerScoutingRequest request =
                new UpsertForeignPlayerScoutingRequest(
                        "2026/2027",
                        "Roma",
                        "Serie A",
                        "IT",
                        "PERMANENT",
                        80,
                        80,
                        20,
                        80,
                        "Trasferimento interno."
                );

        assertThatThrownBy(() ->
                service.upsert(
                        playerId,
                        request
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Italia"
                );
    }

    @Test
    void shouldRejectUnknownRecommendationFilter() {
        assertThatThrownBy(() ->
                service.findAll(
                        "2026/2027",
                        "UNKNOWN"
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Raccomandazione scouting non valida"
                );
    }

    @Test
    void shouldRejectPlayerNotRegisteredForSeason() {
        Long unknownPlayerId = 999999L;

        assertThatThrownBy(() ->
                service.upsert(
                        unknownPlayerId,
                        validRequest()
                )
        )
                .isInstanceOf(
                        ResourceNotFoundException.class
                )
                .hasMessageContaining(
                        "non registrato in Serie A"
                );
    }

    @Test
    void shouldReturnNotFoundForMissingReport() {
        Long playerId = findRegisteredPlayerWithoutReport();

        assertThatThrownBy(() ->
                service.findByPlayer(
                        playerId,
                        "2026/2027"
                )
        )
                .isInstanceOf(
                        ResourceNotFoundException.class
                )
                .hasMessage(
                        "Scouting del giocatore non trovato"
                );
    }

    private UpsertForeignPlayerScoutingRequest validRequest() {
        return new UpsertForeignPlayerScoutingRequest(
                "2026/2027",
                "Ajax",
                "Eredivisie",
                "NL",
                "PERMANENT",
                84,
                91,
                28,
                82,
                "Profilo con ottimo potenziale e buone "
                        + "probabilità di adattamento alla Serie A."
        );
    }

    private Long findRegisteredPlayerId() {
        return jdbcClient.sql("""
                SELECT player.id
                FROM players player

                JOIN player_registrations registration
                    ON registration.player_id = player.id
                    AND registration.registration_status =
                        'ACTIVE'

                JOIN seasons season
                    ON season.id = registration.season_id

                WHERE player.active = TRUE
                  AND season.code = '2026/2027'

                ORDER BY player.id
                LIMIT 1
                """)
                .query(Long.class)
                .single();
    }

    private Long findRegisteredPlayerWithoutReport() {
        List<Map<String, Object>> players =
                jdbcClient.sql("""
                        SELECT player.id
                        FROM players player

                        JOIN player_registrations registration
                            ON registration.player_id = player.id
                            AND registration.registration_status =
                                'ACTIVE'

                        JOIN seasons season
                            ON season.id = registration.season_id

                        WHERE player.active = TRUE
                          AND season.code = '2026/2027'
                          AND NOT EXISTS (
                              SELECT 1
                              FROM foreign_player_scouting_reports
                                  report
                              WHERE report.player_id = player.id
                                AND report.season_id = season.id
                          )

                        ORDER BY player.id
                        LIMIT 1
                        """)
                        .query()
                        .listOfRows();

        assertThat(players)
                .isNotEmpty();

        return ((Number) players.getFirst().get("id"))
                .longValue();
    }
}