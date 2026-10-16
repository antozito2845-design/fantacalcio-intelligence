package it.fantacalciointelligence.backend.statistics.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;

import it.fantacalciointelligence.backend.common.BadRequestException;

@SpringBootTest
class PlayerStatisticsImportServiceTest {

    private static final String TEST_PLAYER_NAME =
            "Statistics Import Test Player";

    private static final String TEST_NORMALIZED_NAME =
            "statistics import test player";

    private static final String TEST_EXTERNAL_ID =
            "STATISTICS-TEST-001";

    private static final String TEST_FILE_PREFIX =
            "statistics-import-test-";

    @Autowired
    private PlayerStatisticsImportService service;

    @Autowired
    private JdbcClient jdbcClient;

    private Long playerId;
    private Long clubId;

    @BeforeEach
    void preparePlayer() {
        cleanTestData();

        clubId = jdbcClient.sql("""
                SELECT id
                FROM clubs
                WHERE slug = 'inter'
                ORDER BY id
                LIMIT 1
                """)
                .query(Long.class)
                .single();

        playerId = jdbcClient.sql("""
                INSERT INTO players (
                    first_name,
                    last_name,
                    display_name,
                    normalized_name,
                    active
                )
                VALUES (
                    'Statistics',
                    'Import Test Player',
                    :displayName,
                    :normalizedName,
                    TRUE
                )
                RETURNING id
                """)
                .param(
                        "displayName",
                        TEST_PLAYER_NAME
                )
                .param(
                        "normalizedName",
                        TEST_NORMALIZED_NAME
                )
                .query(Long.class)
                .single();

        jdbcClient.sql("""
                INSERT INTO player_source_mappings (
                    player_id,
                    source,
                    external_id,
                    external_name,
                    verified
                )
                VALUES (
                    :playerId,
                    'FANTACALCIO',
                    :externalId,
                    :externalName,
                    TRUE
                )
                """)
                .param("playerId", playerId)
                .param(
                        "externalId",
                        TEST_EXTERNAL_ID
                )
                .param(
                        "externalName",
                        TEST_PLAYER_NAME
                )
                .update();
    }

    @AfterEach
    void cleanUp() {
        cleanTestData();
    }

    @Test
    void shouldImportValidAndInvalidStatisticsRows() {
        MockMultipartFile file =
                csvFile(
                        TEST_FILE_PREFIX + "mixed.csv",
                        """
                        external_id,name,club,appearances,starts,minutes_played,average_rating,fantasy_average,goals,assists,penalties_scored,penalties_missed,yellow_cards,red_cards,own_goals,goals_conceded,clean_sheets,penalties_saved,expected_goals,expected_assists
                        STATISTICS-TEST-001,Statistics Import Test Player,Inter,32,28,2450,6.25,6.80,8,7,2,1,4,0,0,0,0,0,7.450,5.200
                        UNKNOWN-PLAYER,Giocatore Inesistente,Inter,10,5,500,6.00,6.10,1,1,0,0,1,0,0,0,0,0,1.000,0.800
                        """
                );

        PlayerStatisticsImportResponse response =
                service.importCsv(
                        "fantacalcio",
                        "2024/2025",
                        file
                );

        assertThat(response.status())
                .isEqualTo("COMPLETED");

        assertThat(response.totalRows())
                .isEqualTo(2);

        assertThat(response.validRows())
                .isEqualTo(1);

        assertThat(response.invalidRows())
                .isEqualTo(1);

        assertThat(response.importedStatistics())
                .isEqualTo(1);

        Map<String, Object> statistics =
                findStatistics();

        assertThat(
                ((Number) statistics.get(
                        "appearances"
                )).intValue()
        ).isEqualTo(32);

        assertThat(
                ((Number) statistics.get(
                        "goals"
                )).intValue()
        ).isEqualTo(8);

        assertThat(
                statistics.get("fantasy_average")
        ).isEqualTo(
                new java.math.BigDecimal("6.80")
        );

        Integer invalidRows =
                jdbcClient.sql("""
                        SELECT COUNT(*)::INTEGER
                        FROM player_statistics_import_rows
                        WHERE batch_id = :batchId
                          AND valid = FALSE
                        """)
                        .param(
                                "batchId",
                                response.batchId()
                        )
                        .query(Integer.class)
                        .single();

        assertThat(invalidRows)
                .isEqualTo(1);
    }

    @Test
    void shouldUpdateExistingStatisticsWithoutDuplicates() {
        service.importCsv(
                "FANTACALCIO",
                "2024/2025",
                csvFile(
                        TEST_FILE_PREFIX + "first.csv",
                        validCsv(25, 4)
                )
        );

        service.importCsv(
                "FANTACALCIO",
                "2024/2025",
                csvFile(
                        TEST_FILE_PREFIX + "second.csv",
                        validCsv(30, 9)
                )
        );

        Integer rows =
                jdbcClient.sql("""
                        SELECT COUNT(*)::INTEGER
                        FROM player_season_statistics statistics

                        JOIN seasons season
                            ON season.id =
                                statistics.season_id

                        WHERE statistics.player_id = :playerId
                          AND statistics.source =
                              'FANTACALCIO'
                          AND season.code = '2024/2025'
                        """)
                        .param("playerId", playerId)
                        .query(Integer.class)
                        .single();

        assertThat(rows)
                .isEqualTo(1);

        Map<String, Object> statistics =
                findStatistics();

        assertThat(
                ((Number) statistics.get(
                        "appearances"
                )).intValue()
        ).isEqualTo(30);

        assertThat(
                ((Number) statistics.get(
                        "goals"
                )).intValue()
        ).isEqualTo(9);
    }

    @Test
    void shouldRejectStartsGreaterThanAppearances() {
        PlayerStatisticsImportResponse response =
                service.importCsv(
                        "FANTACALCIO",
                        "2024/2025",
                        csvFile(
                                TEST_FILE_PREFIX
                                        + "invalid-starts.csv",
                                """
                                external_id,name,club,appearances,starts
                                STATISTICS-TEST-001,Statistics Import Test Player,Inter,10,11
                                """
                        )
                );

        assertThat(response.validRows())
                .isZero();

        assertThat(response.invalidRows())
                .isEqualTo(1);

        String error =
                jdbcClient.sql("""
                        SELECT validation_error
                        FROM player_statistics_import_rows
                        WHERE batch_id = :batchId
                        """)
                        .param(
                                "batchId",
                                response.batchId()
                        )
                        .query(String.class)
                        .single();

        assertThat(error)
                .contains(
                        "titolarità non possono superare"
                );
    }

    @Test
    void shouldPreserveFailedBatchWhenHeadersAreMissing() {
        String filename =
                TEST_FILE_PREFIX
                        + "missing-headers.csv";

        assertThatThrownBy(() ->
                service.importCsv(
                        "GAZZETTA",
                        "2024/2025",
                        csvFile(
                                filename,
                                """
                                player,team
                                Mario Rossi,Inter
                                """
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Colonne obbligatorie mancanti"
                );

        Map<String, Object> batch =
                jdbcClient.sql("""
                        SELECT status, error_message
                        FROM player_statistics_import_batches
                        WHERE original_filename = :filename
                        ORDER BY id DESC
                        LIMIT 1
                        """)
                        .param("filename", filename)
                        .query()
                        .singleRow();

        assertThat(batch.get("status"))
                .isEqualTo("FAILED");

        assertThat(
                String.valueOf(
                        batch.get("error_message")
                )
        ).contains(
                "Colonne obbligatorie mancanti"
        );
    }

    @Test
    void shouldImportFbrefStatisticsAsStatisticalSource() {
        PlayerStatisticsImportResponse response =
                service.importCsv(
                        "fbref",
                        "2024/2025",
                        csvFile(
                                TEST_FILE_PREFIX + "fbref.csv",
                                fbrefCsv(TEST_EXTERNAL_ID, "Inter", 24, 6, playerId,
                                        "EXACT_FULL_NAME")
                        )
                );

        assertThat(response.status())
                .isEqualTo("COMPLETED");

        assertThat(response.source())
                .isEqualTo("FBREF");

        assertThat(response.importedStatistics())
                .isEqualTo(1);

        Map<String, Object> statistics =
                jdbcClient.sql("""
                        SELECT
                            statistics.source,
                            statistics.appearances,
                            statistics.goals,
                            batch.source AS batch_source
                        FROM player_season_statistics statistics
                        JOIN seasons season
                            ON season.id = statistics.season_id
                        JOIN player_statistics_import_batches batch
                            ON batch.id = statistics.import_batch_id
                        WHERE statistics.player_id = :playerId
                          AND statistics.source = 'FBREF'
                          AND season.code = '2024/2025'
                        """)
                        .param("playerId", playerId)
                        .query()
                        .singleRow();

        assertThat(statistics.get("source"))
                .isEqualTo("FBREF");

        assertThat(statistics.get("batch_source"))
                .isEqualTo("FBREF");

        assertThat(((Number) statistics.get("appearances")).intValue())
                .isEqualTo(24);

        assertThat(((Number) statistics.get("goals")).intValue())
                .isEqualTo(6);

        Integer fbrefMappings = jdbcClient.sql("""
                SELECT COUNT(*)::INTEGER FROM player_source_mappings
                WHERE source = 'FBREF'
                """).query(Integer.class).single();
        assertThat(fbrefMappings).isZero();
    }

    @Test
    void shouldPreserveAggregatedFbrefMultiClubStatistics() {
        service.importCsv("FBREF", "2024/2025", csvFile(TEST_FILE_PREFIX + "multiclub.csv",
                fbrefCsv("FBREF-MULTI-001", "Roma|Milan", 29, 8, playerId, "EXACT_FULL_NAME")));

        Map<String, Object> statistics = jdbcClient.sql("""
                SELECT id, club_id, appearances, goals FROM player_season_statistics
                WHERE player_id = :playerId AND source = 'FBREF'
                """).param("playerId", playerId).query().singleRow();
        assertThat(statistics.get("club_id")).isNull();
        assertThat(((Number) statistics.get("appearances")).intValue()).isEqualTo(29);
        assertThat(((Number) statistics.get("goals")).intValue()).isEqualTo(8);

        var clubs = jdbcClient.sql("""
                SELECT club_name FROM player_season_statistics_clubs
                WHERE statistics_id = :statisticsId ORDER BY source_order
                """).param("statisticsId", statistics.get("id")).query(String.class).list();
        assertThat(clubs).containsExactly("Roma", "Milan");
    }

    @Test
    void shouldReuseSameFbrefIdAcrossSeasonsWithoutIdentityMapping() {
        service.importCsv("FBREF", "2024/2025", csvFile(TEST_FILE_PREFIX + "first-season.csv",
                fbrefCsv("FBREF-CONTINUITY-001", "Roma", 20, 4, playerId, "EXACT_FULL_NAME")));
        service.importCsv("FBREF", "2025/2026", csvFile(TEST_FILE_PREFIX + "second-season.csv",
                fbrefCsv("FBREF-CONTINUITY-001", "Napoli", 30, 7, playerId,
                        "CROSS_SEASON_EXACT_ANCHOR")));

        Integer rows = jdbcClient.sql("""
                SELECT COUNT(*)::INTEGER FROM player_season_statistics
                WHERE player_id = :playerId AND source = 'FBREF'
                """).param("playerId", playerId).query(Integer.class).single();
        assertThat(rows).isEqualTo(2);
    }

    @Test
    void shouldRejectDifferentFbrefIdsForSamePlayer() {
        service.importCsv("FBREF", "2024/2025", csvFile(TEST_FILE_PREFIX + "first-id.csv",
                fbrefCsv("FBREF-ID-A", "Inter", 20, 4, playerId, "EXACT_FULL_NAME")));
        PlayerStatisticsImportResponse response = service.importCsv("FBREF", "2025/2026",
                csvFile(TEST_FILE_PREFIX + "second-id.csv",
                        fbrefCsv("FBREF-ID-B", "Inter", 20, 4, playerId, "EXACT_FULL_NAME")));
        assertThat(response.validRows()).isZero();
        assertThat(response.invalidRows()).isEqualTo(1);
    }

    @Test
    void shouldNeverImportFbrefReviewCandidate() {
        PlayerStatisticsImportResponse response = service.importCsv("FBREF", "2024/2025",
                csvFile(TEST_FILE_PREFIX + "review.csv", """
                        external_id,name,clubs_played,appearances,candidate_player_id,match_status,match_method,match_confidence
                        FBREF-REVIEW-001,Ambiguous Player,Inter,10,%s,REVIEW,SURNAME_ONLY_CANDIDATE,LOW
                        """.formatted(playerId)));
        assertThat(response.importedStatistics()).isZero();
        assertThat(response.invalidRows()).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidSourceBeforeCreatingBatch() {
        String filename =
                TEST_FILE_PREFIX
                        + "invalid-source.csv";

        assertThatThrownBy(() ->
                service.importCsv(
                        "INVALID_SOURCE",
                        "2024/2025",
                        csvFile(
                                filename,
                                validCsv(20, 3)
                        )
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Fonte non valida"
                );

        Integer batches =
                jdbcClient.sql("""
                        SELECT COUNT(*)::INTEGER
                        FROM player_statistics_import_batches
                        WHERE original_filename = :filename
                        """)
                        .param("filename", filename)
                        .query(Integer.class)
                        .single();

        assertThat(batches)
                .isZero();
    }

    @Test
    void shouldPreserveNullDistinctFromKnownZero() {
        service.importCsv("FANTACALCIO", "2024/2025", csvFile(TEST_FILE_PREFIX + "nulls.csv", """
                external_id,name,club,appearances,goals,assists
                STATISTICS-TEST-001,Statistics Import Test Player,Inter,10,0,
                """));

        Map<String, Object> row = jdbcClient.sql("""
                SELECT goals, assists, starts FROM player_season_statistics statistics
                JOIN seasons season ON season.id = statistics.season_id
                WHERE statistics.player_id = :playerId AND season.code = '2024/2025'
                """).param("playerId", playerId).query().singleRow();
        assertThat(((Number) row.get("goals")).intValue()).isZero();
        assertThat(row.get("assists")).isNull();
        assertThat(row.get("starts")).isNull();
    }

    @Test
    void shouldKeepDifferentSourcesAndSeasonsIndependent() {
        service.importCsv("FANTACALCIO", "2024/2025",
                csvFile(TEST_FILE_PREFIX + "fanta.csv", validCsv(25, 4)));
        service.importCsv("GAZZETTA", "2024/2025",
                csvFile(TEST_FILE_PREFIX + "gazzetta.csv", validCsv(26, 5)));
        service.importCsv("FANTACALCIO", "2025/2026",
                csvFile(TEST_FILE_PREFIX + "next-season.csv", validCsv(30, 8)));

        Integer rows = jdbcClient.sql("""
                SELECT COUNT(*)::INTEGER FROM player_season_statistics
                WHERE player_id = :playerId
                """).param("playerId", playerId).query(Integer.class).single();
        assertThat(rows).isEqualTo(3);
    }

    @Test
    void shouldImportGoalkeeperStatisticsAndLineage() {
        PlayerStatisticsImportResponse response = service.importCsv("FANTACALCIO", "2024/2025",
                csvFile(TEST_FILE_PREFIX + "goalkeeper.csv", """
                external_id,name,club,appearances,minutes_played,goals_conceded,clean_sheets,saves,penalties_saved
                STATISTICS-TEST-001,Statistics Import Test Player,Inter,20,1800,18,9,74,2
                """));
        Map<String, Object> row = jdbcClient.sql("""
                SELECT goals_conceded, clean_sheets, saves, penalties_saved, import_batch_id,
                       source_external_id
                FROM player_season_statistics WHERE player_id = :playerId
                """).param("playerId", playerId).query().singleRow();
        assertThat(((Number) row.get("saves")).intValue()).isEqualTo(74);
        assertThat(((Number) row.get("penalties_saved")).intValue()).isEqualTo(2);
        assertThat(((Number) row.get("import_batch_id")).longValue()).isEqualTo(response.batchId());
        assertThat(row.get("source_external_id")).isEqualTo(TEST_EXTERNAL_ID);
    }

    private String validCsv(
            int appearances,
            int goals
    ) {
        return """
                external_id,name,club,appearances,starts,minutes_played,average_rating,fantasy_average,goals,assists
                %s,%s,Inter,%d,20,1800,6.10,6.50,%d,5
                """.formatted(
                TEST_EXTERNAL_ID,
                TEST_PLAYER_NAME,
                appearances,
                goals
        );
    }

    private String fbrefCsv(String externalId, String clubs, int appearances, int goals,
            Long candidatePlayerId, String method) {
        return """
                external_id,name,clubs_played,appearances,goals,assists,candidate_player_id,match_status,match_method,match_confidence
                %s,Statistics Import Test Player,%s,%d,%d,,%d,MATCHED,%s,HIGH
                """.formatted(externalId, clubs, appearances, goals, candidatePlayerId, method);
    }

    private MockMultipartFile csvFile(
            String filename,
            String content
    ) {
        return new MockMultipartFile(
                "file",
                filename,
                "text/csv",
                content.getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private Map<String, Object> findStatistics() {
        return jdbcClient.sql("""
                SELECT
                    statistics.appearances,
                    statistics.goals,
                    statistics.fantasy_average
                FROM player_season_statistics statistics

                JOIN seasons season
                    ON season.id =
                        statistics.season_id

                WHERE statistics.player_id = :playerId
                  AND statistics.source =
                      'FANTACALCIO'
                  AND season.code = '2024/2025'
                """)
                .param("playerId", playerId)
                .query()
                .singleRow();
    }

    private void cleanTestData() {
        jdbcClient.sql("""
                DELETE FROM player_season_statistics
                WHERE player_id IN (
                    SELECT id
                    FROM players
                    WHERE normalized_name =
                        :normalizedName
                )
                """)
                .param(
                        "normalizedName",
                        TEST_NORMALIZED_NAME
                )
                .update();

        jdbcClient.sql("""
                DELETE FROM player_statistics_import_batches
                WHERE original_filename
                    LIKE :filenamePattern
                """)
                .param(
                        "filenamePattern",
                        TEST_FILE_PREFIX + "%"
                )
                .update();

        jdbcClient.sql("""
                DELETE FROM player_source_mappings
                WHERE external_id = :externalId
                """)
                .param(
                        "externalId",
                        TEST_EXTERNAL_ID
                )
                .update();

        jdbcClient.sql("""
                DELETE FROM player_statistical_source_links
                WHERE player_id IN (
                    SELECT id FROM players WHERE normalized_name = :normalizedName
                )
                """).param("normalizedName", TEST_NORMALIZED_NAME).update();

        jdbcClient.sql("""
                DELETE FROM player_registrations
                WHERE player_id IN (
                    SELECT id
                    FROM players
                    WHERE normalized_name =
                        :normalizedName
                )
                """)
                .param(
                        "normalizedName",
                        TEST_NORMALIZED_NAME
                )
                .update();

        jdbcClient.sql("""
                DELETE FROM players
                WHERE normalized_name =
                    :normalizedName
                """)
                .param(
                        "normalizedName",
                        TEST_NORMALIZED_NAME
                )
                .update();
    }
}
