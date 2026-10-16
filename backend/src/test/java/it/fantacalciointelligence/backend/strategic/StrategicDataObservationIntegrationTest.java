package it.fantacalciointelligence.backend.strategic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class StrategicDataObservationIntegrationTest {
    private static final String PLAYER_NAME = "B55 Database Certification Player";

    @Autowired StrategicDataObservationService service;
    @Autowired StrategicDataInspectionService inspectionService;
    @Autowired JdbcClient jdbcClient;

    private Long playerId;
    private Long season2025;
    private Long season2026;
    private Long clubId;

    @BeforeEach
    void setUp() {
        clean();
        season2025 = season("2025/2026");
        season2026 = season("2026/2027");
        clubId = jdbcClient.sql("SELECT id FROM clubs ORDER BY id LIMIT 1").query(Long.class).single();
        playerId = jdbcClient.sql("""
                INSERT INTO players (last_name, display_name, normalized_name, active)
                VALUES ('Certification Player', :name, 'b55 database certification player', TRUE)
                RETURNING id
                """).param("name", PLAYER_NAME).query(Long.class).single();
    }

    @AfterEach
    void tearDown() { clean(); }

    @Test
    void upsertIsIdempotentAndUpdatesSameSourceRecordWithoutCreatingPlayer() {
        long playersBefore = playerCount();
        Long firstId = service.upsert(observation(season2026, "FBREF", "fbref-1", 10, "1.2000", 900));
        Long secondId = service.upsert(observation(season2026, "FBREF", "fbref-1", 14, "1.8000", 990));

        assertThat(secondId).isEqualTo(firstId);
        assertThat(observationCount()).isEqualTo(1);
        assertThat(playerCount()).isEqualTo(playersBefore);
        Map<String, Object> stored = stored(firstId);
        assertThat(((Number) stored.get("shots")).intValue()).isEqualTo(14);
        assertThat((BigDecimal) stored.get("expected_goals")).isEqualByComparingTo("1.8000");
        assertThat(stored.get("source")).isEqualTo("FBREF");
        assertThat(stored.get("source_record_id")).isEqualTo("fbref-1");
    }

    @Test
    void nullSourceRecordUsesPartialUniqueIndexIdempotently() {
        Long firstId = service.upsert(observation(season2026, "FANTACALCIO", null, 4, null, null));
        Long secondId = service.upsert(observation(season2026, "FANTACALCIO", null, 5, null, null));
        assertThat(secondId).isEqualTo(firstId);
        assertThat(observationCount()).isEqualTo(1);
        assertThat(stored(firstId).get("source_record_id")).isNull();
    }

    @Test
    void conflictingSourcesRemainSeparateAndInspectionDoesNotMergeThem() {
        service.upsert(observation(season2026, "FBREF", "conflict-fbref", 20, "3.1000", 1000));
        service.upsert(observation(season2026, "GAZZETTA", "conflict-gazzetta", 11, "1.7000", 800));

        List<StrategicObservationResponse> rows = inspectionService.findObservations(playerId, "2026/2027");
        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(StrategicObservationResponse::source)
                .containsExactly("FBREF", "GAZZETTA");
        assertThat(rows).extracting(StrategicObservationResponse::shots).containsExactly(20, 11);
        assertThat(rows).extracting(StrategicObservationResponse::evidenceStatus)
                .containsOnly("AVAILABLE");
        assertThat(rows).extracting(StrategicObservationResponse::confidenceScore)
                .containsOnly(new BigDecimal("80.00"));
    }

    @Test
    void seasonsAreIsolatedDuringInspectionAndUpdate() {
        Long historicalId = service.upsert(observation(season2025, "FBREF", "same-record", 7, "0.7000", 700));
        service.upsert(observation(season2026, "FBREF", "same-record", 12, "1.2000", 900));
        service.upsert(observation(season2026, "FBREF", "same-record", 15, "1.5000", 950));

        assertThat(inspectionService.findObservations(playerId, "2025/2026"))
                .singleElement().extracting(StrategicObservationResponse::shots).isEqualTo(7);
        assertThat(inspectionService.findObservations(playerId, "2026/2027"))
                .singleElement().extracting(StrategicObservationResponse::shots).isEqualTo(15);
        assertThat(((Number) stored(historicalId).get("shots")).intValue()).isEqualTo(7);
    }

    @Test
    void inactiveRegistrationDoesNotDeleteHistoricalObservation() {
        jdbcClient.sql("""
                INSERT INTO player_registrations (player_id, club_id, season_id, registration_status)
                VALUES (:playerId, :clubId, :seasonId, 'INACTIVE')
                """).param("playerId", playerId).param("clubId", clubId).param("seasonId", season2025).update();
        service.upsert(observation(season2025, "FBREF", "historical", 6, null, 540));

        assertThat(inspectionService.findObservations(playerId, "2025/2026")).hasSize(1);
        assertThat(jdbcClient.sql("SELECT registration_status FROM player_registrations WHERE player_id=:id")
                .param("id", playerId).query(String.class).single()).isEqualTo("INACTIVE");
        assertThat(observationCount()).isEqualTo(1);
    }

    @Test
    void nullUnknownAndZeroMinuteValuesRemainUninvented() {
        StrategicObservationUpsert value = new StrategicObservationUpsert(
                playerId, season2026, "FBREF", "nulls", OffsetDateTime.parse("2026-08-01T10:00:00Z"),
                "PARTIAL", null, "generic defender", RealPosition.UNKNOWN, "UNKNOWN",
                null, null, null, null, null, null, null, null, null,
                1, 0, 0, null, "{\"basis\":\"partial\"}");
        service.upsert(value);
        service.upsert(new StrategicObservationUpsert(
                playerId, season2026, "GAZZETTA", "missing-minutes", null, "PARTIAL",
                null, null, null, "UNKNOWN", 3, 1, null, null, null, null,
                null, null, null, 1, 0, null, null, "{}"));

        List<StrategicObservationResponse> rows = inspectionService.findObservations(playerId, "2026/2027");
        StrategicObservationResponse row = rows.stream().filter(item -> item.source().equals("FBREF")).findFirst().orElseThrow();
        assertThat(row.expectedGoals()).isNull();
        assertThat(row.expectedAssists()).isNull();
        assertThat(row.aerialDuels()).isNull();
        assertThat(row.penaltyHierarchy()).isEqualTo("UNKNOWN");
        assertThat(row.realPositionRaw()).isEqualTo("generic defender");
        assertThat(row.realPositionNormalized()).isEqualTo(RealPosition.UNKNOWN);
        assertThat(row.shotsPer90()).isNull();
        assertThat(row.expectedGoalsPer90()).isNull();
        assertThat(row.evidence()).contains("partial");
        StrategicObservationResponse missingMinutes = rows.stream()
                .filter(item -> item.source().equals("GAZZETTA")).findFirst().orElseThrow();
        assertThat(missingMinutes.shots()).isEqualTo(3);
        assertThat(missingMinutes.minutesPlayed()).isNull();
        assertThat(missingMinutes.shotsPer90()).isNull();
    }

    @Test
    void migrationRejectsInvalidForeignKeysSourceEvidenceConfidenceAndDuplicates() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO player_strategic_data_observations
                    (player_id, season_id, source, source_record_id, evidence_status, evidence)
                VALUES (-999999, :seasonId, 'FBREF', 'bad-fk', 'AVAILABLE', '{}'::jsonb)
                """).param("seasonId", season2026).update()).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> service.upsert(new StrategicObservationUpsert(
                playerId, season2026, "UNTRUSTED", "bad-source", null, "AVAILABLE",
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> service.upsert(new StrategicObservationUpsert(
                playerId, season2026, "FBREF", "bad-confidence", null, "AVAILABLE",
                new BigDecimal("100.01"), null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> service.upsert(new StrategicObservationUpsert(
                playerId, season2026, "FBREF", "bad-evidence", null, "CERTAIN",
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "{}")))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long id = service.upsert(observation(season2026, "FBREF", "unique", 1, null, null));
        assertThat(id).isNotNull();
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO player_strategic_data_observations
                    (player_id, season_id, source, source_record_id, evidence_status, evidence)
                VALUES (:playerId, :seasonId, 'FBREF', 'unique', 'AVAILABLE', '{}'::jsonb)
                """).param("playerId", playerId).param("seasonId", season2026).update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private StrategicObservationUpsert observation(Long seasonId, String source, String recordId,
            Integer shots, String expectedGoals, Integer minutes) {
        return new StrategicObservationUpsert(
                playerId, seasonId, source, recordId, OffsetDateTime.parse("2026-08-01T10:00:00Z"),
                "AVAILABLE", new BigDecimal("80"), null, null, "UNKNOWN", shots,
                shots, expectedGoals == null ? null : new BigDecimal(expectedGoals), null,
                null, null, null, null, null, 10, 8, minutes, null,
                "{\"certified\":true}");
    }

    private Long season(String code) {
        return jdbcClient.sql("SELECT id FROM seasons WHERE code=:code")
                .param("code", code).query(Long.class).single();
    }

    private Map<String, Object> stored(Long id) {
        return jdbcClient.sql("SELECT * FROM player_strategic_data_observations WHERE id=:id")
                .param("id", id).query().singleRow();
    }

    private long observationCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM player_strategic_data_observations WHERE player_id=:id")
                .param("id", playerId).query(Long.class).single();
    }

    private long playerCount() {
        return jdbcClient.sql("SELECT COUNT(*) FROM players WHERE id=:id")
                .param("id", playerId).query(Long.class).single();
    }

    private void clean() {
        jdbcClient.sql("DELETE FROM player_strategic_data_observations WHERE player_id IN (SELECT id FROM players WHERE display_name=:name)")
                .param("name", PLAYER_NAME).update();
        jdbcClient.sql("DELETE FROM player_registrations WHERE player_id IN (SELECT id FROM players WHERE display_name=:name)")
                .param("name", PLAYER_NAME).update();
        jdbcClient.sql("DELETE FROM players WHERE display_name=:name").param("name", PLAYER_NAME).update();
    }
}
