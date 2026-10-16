package it.fantacalciointelligence.backend.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

@SpringBootTest
@Transactional
class PlayerAvailabilityServiceTest {

    @Autowired
    private PlayerAvailabilityService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldUpdateAvailabilityAndCreateHistory() {
        Long playerId = findActivePlayerId();

        PlayerAvailabilityResponse response =
                service.update(
                        playerId,
                        new UpdatePlayerAvailabilityRequest(
                                "2026/2027",
                                2,
                                "injured",
                                0,
                                0,
                                "Problema muscolare",
                                LocalDate.of(2026, 8, 20),
                                "manual"
                        )
                );

        assertThat(response.playerId())
                .isEqualTo(playerId);

        assertThat(response.status())
                .isEqualTo("INJURED");

        assertThat(response.startingProbability())
                .isZero();

        assertThat(response.availabilityProbability())
                .isZero();

        assertThat(response.injuryDescription())
                .isEqualTo("Problema muscolare");

        assertThat(response.updatedAt())
                .isNotNull();

        List<PlayerAvailabilityHistoryResponse> history =
                service.findHistory(
                        playerId,
                        "2026/2027",
                        2
                );

        assertThat(history)
                .hasSize(1);

        assertThat(history.getFirst().status())
                .isEqualTo("INJURED");

        assertThat(history.getFirst().source())
                .isEqualTo("MANUAL");
    }

    @Test
    void shouldCreateMultipleHistoryEntriesForSameAvailability() {
        Long playerId = findActivePlayerId();

        service.update(
                playerId,
                new UpdatePlayerAvailabilityRequest(
                        "2026/2027",
                        3,
                        "DOUBTFUL",
                        40,
                        60,
                        null,
                        null,
                        "FANTACALCIO"
                )
        );

        service.update(
                playerId,
                new UpdatePlayerAvailabilityRequest(
                        "2026/2027",
                        3,
                        "AVAILABLE",
                        85,
                        100,
                        null,
                        null,
                        "GAZZETTA"
                )
        );

        List<PlayerAvailabilityHistoryResponse> history =
                service.findHistory(
                        playerId,
                        "2026/2027",
                        3
                );

        assertThat(history)
                .hasSize(2);

        assertThat(history)
                .extracting(
                        PlayerAvailabilityHistoryResponse::status
                )
                .containsExactly(
                        "AVAILABLE",
                        "DOUBTFUL"
                );
    }

    @Test
    void shouldRejectInjuredPlayerWithoutDescription() {
        Long playerId = findActivePlayerId();

        assertThatThrownBy(() ->
                service.update(
                        playerId,
                        new UpdatePlayerAvailabilityRequest(
                                "2026/2027",
                                4,
                                "INJURED",
                                0,
                                0,
                                null,
                                LocalDate.of(2026, 8, 25),
                                "MANUAL"
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "descrizione dell'infortunio"
                );
    }

    @Test
    void shouldRejectPositiveProbabilityForSuspendedPlayer() {
        Long playerId = findActivePlayerId();

        assertThatThrownBy(() ->
                service.update(
                        playerId,
                        new UpdatePlayerAvailabilityRequest(
                                "2026/2027",
                                5,
                                "SUSPENDED",
                                20,
                                30,
                                "Squalificato",
                                null,
                                "MANUAL"
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "probabilità superiori a zero"
                );
    }

    @Test
    void shouldRejectUnknownStatusFilter() {
        assertThatThrownBy(() ->
                service.findByMatchday(
                        "2026/2027",
                        1,
                        "UNKNOWN"
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Stato di disponibilità non valido"
                );
    }

    @Test
    void shouldRejectUnknownPlayer() {
        assertThatThrownBy(() ->
                service.findHistory(
                        999999L,
                        "2026/2027",
                        null
                )
        )
                .isInstanceOf(
                        ResourceNotFoundException.class
                )
                .hasMessage(
                        "Giocatore non trovato"
                );
    }

    private Long findActivePlayerId() {
        return jdbcClient.sql("""
                SELECT player.id
                FROM players player
                JOIN player_registrations registration
                    ON registration.player_id = player.id
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
}