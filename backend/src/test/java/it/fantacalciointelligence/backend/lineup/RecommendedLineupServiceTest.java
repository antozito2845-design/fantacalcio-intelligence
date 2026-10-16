package it.fantacalciointelligence.backend.lineup;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class RecommendedLineupServiceTest {

    @Autowired
    private RecommendedLineupService recommendedLineupService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldCreateCompleteRecommendedLineup() {
        RecommendedLineupResponse response =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        null
                );

        assertThat(response).isNotNull();
        assertThat(response.squadId()).isNotNull();
        assertThat(response.complete()).isTrue();
        assertThat(response.selectedPlayers()).isEqualTo(11);
        assertThat(response.starters()).hasSize(11);
        assertThat(response.formation()).isNotBlank();
    }

    @Test
    void shouldRespectMaximumSubstitutionLimit() {
        RecommendedLineupResponse response =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        "18,3,12,5,7"
                );

        assertThat(response.maxSubstitutions()).isEqualTo(3);
        assertThat(response.usedSubstitutions()).isEqualTo(3);
        assertThat(response.remainingSubstitutions()).isZero();
        assertThat(response.substitutions()).hasSize(3);
        assertThat(response.warnings())
                .contains(
                        "Limite massimo di 3 sostituzioni raggiunto."
                );
    }

    @Test
    void shouldKeepAllSubstitutionsAvailableWithoutAbsences() {
        RecommendedLineupResponse response =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        null
                );

        assertThat(response.usedSubstitutions()).isZero();
        assertThat(response.remainingSubstitutions())
                .isEqualTo(response.maxSubstitutions());
        assertThat(response.substitutions()).isEmpty();
        assertThat(response.warnings())
                .doesNotContain(
                        "Limite massimo di 3 sostituzioni raggiunto."
                );
    }

    @Test
    void shouldIgnoreUnknownOrNonStarterPlayerIds() {
        RecommendedLineupResponse response =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        "999999,22"
                );

        assertThat(response.complete()).isTrue();
        assertThat(response.usedSubstitutions()).isZero();
        assertThat(response.substitutions()).isEmpty();
        assertThat(response.remainingSubstitutions())
                .isEqualTo(response.maxSubstitutions());
    }

    @Test
    void shouldUseOneSubstitutionWithoutLimitWarning() {
        RecommendedLineupResponse initialResponse =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        null
                );

        long replaceableStarterId =
                initialResponse.starters()
                        .stream()
                        .filter(starter ->
                                initialResponse.bench()
                                        .stream()
                                        .anyMatch(reserve ->
                                                reserve.role().equals(
                                                        starter.role()
                                                )
                                                        && (
                                                        reserve.availabilityStatus()
                                                                .equals("AVAILABLE")
                                                        || reserve.availabilityStatus()
                                                                .equals("PROBABLE")
                                                        || reserve.availabilityStatus()
                                                                .equals("DOUBTFUL")
                                                )
                                        )
                        )
                        .findFirst()
                        .orElseThrow()
                        .playerId();

        RecommendedLineupResponse response =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        String.valueOf(
                                replaceableStarterId
                        )
                );

        assertThat(response.usedSubstitutions()).isEqualTo(1);
        assertThat(response.remainingSubstitutions())
                .isEqualTo(
                        response.maxSubstitutions() - 1
                );
        assertThat(response.substitutions()).hasSize(1);
        assertThat(response.warnings())
                .doesNotContain(
                        "Limite massimo di 3 sostituzioni raggiunto."
                );
    }

    @Test
    void shouldRejectNonNumericUnavailablePlayerIds() {
        assertThatThrownBy(() ->
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        "18,abc,3"
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "unavailablePlayerIds deve contenere solo ID numerici separati da virgole"
                );
    }

    @Test
    @Transactional
    void shouldWarnWhenNoValidReserveIsAvailable() {
        RecommendedLineupResponse initialResponse =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        null
                );

        LineupPlayerResponse starter =
                initialResponse.starters()
                        .stream()
                        .filter(candidate ->
                                initialResponse.bench()
                                        .stream()
                                        .anyMatch(reserve ->
                                                reserve.role().equals(
                                                        candidate.role()
                                                )
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        initialResponse.bench()
                .stream()
                .filter(reserve ->
                        reserve.role().equals(
                                starter.role()
                        )
                )
                .forEach(reserve ->
                        jdbcClient.sql("""
                                UPDATE player_availabilities
                                SET status = 'UNAVAILABLE',
                                    starting_probability = 0,
                                    availability_probability = 0
                                WHERE player_id = :playerId
                                  AND matchday = 1
                                  AND season_id = (
                                      SELECT id
                                      FROM seasons
                                      WHERE code = '2026/2027'
                                  )
                                """)
                                .param(
                                        "playerId",
                                        reserve.playerId()
                                )
                                .update()
                );

        RecommendedLineupResponse response =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        String.valueOf(
                                starter.playerId()
                        )
                );

        assertThat(response.usedSubstitutions()).isZero();
        assertThat(response.substitutions()).isEmpty();
        assertThat(response.warnings())
                .contains(
                        "Una o più assenze non possono essere coperte: nessuna riserva valida disponibile."
                );
    }

    @Test
    void shouldUseMaximumSubstitutionsFromSquadLeagueConfiguration() {
        Long squadId =
                jdbcClient.sql("""
                        SELECT id
                        FROM fantasy_squads
                        WHERE active = TRUE
                        ORDER BY id
                        LIMIT 1
                        """)
                        .query(Long.class)
                        .single();

        Integer expectedMaxSubstitutions =
                jdbcClient.sql("""
                        SELECT league.max_substitutions

                        FROM fantasy_squads squad

                        JOIN league_configurations league
                            ON league.id =
                                squad.league_configuration_id

                        WHERE squad.id = :squadId
                        """)
                        .param("squadId", squadId)
                        .query(Integer.class)
                        .single();

        RecommendedLineupResponse response =
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        squadId,
                        null
                );

        assertThat(response.squadId())
                .isEqualTo(squadId);

        assertThat(response.maxSubstitutions())
                .isEqualTo(
                        expectedMaxSubstitutions
                );

        assertThat(response.remainingSubstitutions())
                .isEqualTo(
                        expectedMaxSubstitutions
                                - response.usedSubstitutions()
                );
    }

    @Test
    void shouldRejectUnknownSquadId() {
        assertThatThrownBy(() ->
                recommendedLineupService.recommend(
                        "2026/2027",
                        1,
                        999999L,
                        null
                )
        )
                .isInstanceOf(
                        it.fantacalciointelligence.backend
                                .common.ResourceNotFoundException.class
                )
                .hasMessageContaining(
                        "Rosa attiva non trovata"
                );
    }}