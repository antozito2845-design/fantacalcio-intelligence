package it.fantacalciointelligence.backend.fixture.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FixtureImportServiceTest {

    @Autowired
    private FixtureImportService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldImportValidFixtureAndRejectUnknownClub() {
        String csv = """
                matchday,home_club,away_club,kickoff_at,status,home_goals,away_goals
                10,Inter,Napoli,2026-10-31T20:45:00+01:00,SCHEDULED,,
                11,Inter,Club Inesistente,2026-11-07T20:45:00+01:00,SCHEDULED,,
                """;

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "fixtures-test.csv",
                        "text/csv",
                        csv.getBytes(StandardCharsets.UTF_8)
                );

        FixtureImportResponse response =
                service.importCsv(
                        "LEGA_SERIE_A",
                        "2026/2027",
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

        assertThat(response.importedFixtures())
                .isEqualTo(1);

        Integer importedFixtureCount =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fixtures fixture

                        JOIN seasons season
                            ON season.id = fixture.season_id

                        JOIN clubs home_club
                            ON home_club.id =
                                fixture.home_club_id

                        JOIN clubs away_club
                            ON away_club.id =
                                fixture.away_club_id

                        WHERE season.code = '2026/2027'
                          AND fixture.matchday = 10
                          AND home_club.slug = 'inter'
                          AND away_club.slug = 'napoli'
                        """)
                        .query(Integer.class)
                        .single();

        assertThat(importedFixtureCount)
                .isEqualTo(1);

        Integer invalidRowCount =
                jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM fixture_import_rows
                        WHERE batch_id = :batchId
                          AND valid = FALSE
                        """)
                        .param(
                                "batchId",
                                response.batchId()
                        )
                        .query(Integer.class)
                        .single();

        assertThat(invalidRowCount)
                .isEqualTo(1);
    }

    @Test
    void shouldRejectGloballyExistingClubOutsideRequestedSeason() {
        Long seasonId = jdbcClient.sql("SELECT id FROM seasons WHERE code='2026/2027'")
                .query(Long.class).single();
        Long napoliId = jdbcClient.sql("SELECT id FROM clubs WHERE slug='napoli'")
                .query(Long.class).single();
        jdbcClient.sql("DELETE FROM season_clubs WHERE season_id=:season AND club_id=:club")
                .param("season", seasonId).param("club", napoliId).update();

        MockMultipartFile file = new MockMultipartFile("file", "season-membership.csv", "text/csv", """
                matchday,home_club,away_club
                12,Inter,Napoli
                """.getBytes(StandardCharsets.UTF_8));

        FixtureImportResponse response = service.importCsv("LEGA_SERIE_A", "2026/2027", file);

        assertThat(response.validRows()).isZero();
        assertThat(response.invalidRows()).isEqualTo(1);
        assertThat(response.importedFixtures()).isZero();
    }
}
