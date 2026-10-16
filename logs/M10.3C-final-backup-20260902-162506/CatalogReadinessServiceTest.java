package it.fantacalciointelligence.backend.catalog.readiness;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogReadinessServiceTest {

    @Autowired
    private CatalogReadinessService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void validCatalogIsReadyAndRoleCountsAreCorrect() {
        TestCatalog catalog = createCatalog("2090/2091", 2090);
        createPlayer(catalog, "Portiere", "P", true, true);
        createPlayer(catalog, "Difensore", "D", true, true);
        createPlayer(catalog, "Centrocampista", "C", true, true);
        createPlayer(catalog, "Attaccante", "A", true, true);

        CatalogReadinessService.QualityReport report = service.evaluateCatalogQuality(catalog.season());

        assertThat(report.status()).isEqualTo("READY");
        assertThat(report.errorCount()).isZero();
        assertThat(report.totalOperationalPlayers()).isEqualTo(4);
        assertThat(report.playersByRole()).isEqualTo(new CatalogRoleSummary(1, 1, 1, 1));
        assertThat(report.clubSummaries()).singleElement().satisfies(club -> {
            assertThat(club.totalPlayers()).isEqualTo(4);
            assertThat(List.of(club.P(), club.D(), club.C(), club.A())).containsExactly(1, 1, 1, 1);
        });
    }

    @Test
    void oneIndependentFantacalcioSourceKeepsCatalogReady() {
        TestCatalog catalog = createCatalog("2091/2092", 2091);
        createPlayer(catalog, "Solo Fantacalcio", "C", false, true);

        CatalogReadinessService.QualityReport report =
                service.evaluateCatalogQuality(
                        catalog.season()
                );

        assertThat(report.status())
                .isEqualTo("READY");

        assertThat(report.errorCount())
                .isZero();

        assertThat(report.issues())
                .noneMatch(issue ->
                        "INSUFFICIENT_IDENTITY_SOURCES"
                                .equals(issue.code())
                );
    }

    @Test
    void excludedPlayerOutsideCatalogDoesNotProduceError() {
        TestCatalog catalog = createCatalog("2092/2093", 2092);
        createPlayer(catalog, "Valido", "A", true, true);
        insertReview(catalog, "FC-EXCLUDED-OUT", "Escluso fuori catalogo", "EXCLUDED");

        CatalogReadinessService.QualityReport report = service.evaluateCatalogQuality(catalog.season());

        assertThat(report.issues()).noneMatch(issue -> "EXCLUDED_PLAYER_IN_CATALOG".equals(issue.code()));
        assertThat(report.status()).isEqualTo("READY");
    }

    @Test
    void excludedPlayerInsideCatalogBlocksCatalog() {
        TestCatalog catalog = createCatalog("2093/2094", 2093);
        PlayerIds player = createPlayer(catalog, "Escluso nel catalogo", "D", true, true);
        insertReview(catalog, player.fantacalcioExternalId(), "Escluso nel catalogo", "EXCLUDED");

        assertBlockedWith(service.evaluateCatalogQuality(catalog.season()), "EXCLUDED_PLAYER_IN_CATALOG");
    }

    @Test
    void openReviewBlocksCatalog() {
        TestCatalog catalog = createCatalog("2094/2095", 2094);
        createPlayer(catalog, "Valido", "A", true, true);
        insertReview(catalog, "FC-OPEN", "Da verificare", "OPEN");

        assertBlockedWith(service.evaluateCatalogQuality(catalog.season()), "OPEN_RECONCILIATION_REVIEW");
    }

    @Test
    @Order(1)
    void invalidFantacalcioRoleBlocksCatalog() {
        TestCatalog catalog = createCatalog("2095/2096", 2095);
        PlayerIds player = createPlayer(catalog, "Ruolo errato", "C", true, true);
        jdbcClient.sql("ALTER TABLE player_roles DROP CONSTRAINT ck_fantasy_role").update();
        jdbcClient.sql("UPDATE player_roles SET fantasy_role = 'X' WHERE player_id = :playerId")
                .param("playerId", player.playerId()).update();

        assertBlockedWith(service.evaluateCatalogQuality(catalog.season()), "INVALID_FANTACALCIO_ROLE");
    }

    @Test
    @Order(2)
    void transactionalDdlAndSyntheticDataWereRolledBack() {
        int constraintCount = jdbcClient.sql("""
                SELECT COUNT(*)
                FROM pg_constraint constraint_definition
                JOIN pg_class table_definition
                  ON table_definition.oid = constraint_definition.conrelid
                WHERE table_definition.relname = 'player_roles'
                  AND constraint_definition.conname = 'ck_fantasy_role'
                """).query(Integer.class).single();
        int syntheticSeasonCount = jdbcClient.sql("""
                SELECT COUNT(*) FROM seasons WHERE code = '2095/2096'
                """).query(Integer.class).single();
        int activeClubCount = jdbcClient.sql("""
                SELECT COUNT(*) FROM clubs WHERE active = TRUE
                """).query(Integer.class).single();

        assertThat(constraintCount).isEqualTo(1);
        assertThat(syntheticSeasonCount).isZero();
        assertThat(activeClubCount).isEqualTo(20);
    }

    @Test
    void missingOrInvalidClubBlocksCatalog() {
        TestCatalog catalog = createCatalog("2096/2097", 2096);
        PlayerIds missing = createPlayer(catalog, "Senza club", "C", true, false);
        createPlayer(catalog, "Club inattivo", "D", true, true);
        jdbcClient.sql("DELETE FROM season_clubs WHERE season_id = :seasonId AND club_id = :clubId")
                .param("seasonId", catalog.seasonId()).param("clubId", catalog.clubId()).update();

        CatalogReadinessService.QualityReport report = service.evaluateCatalogQuality(catalog.season());

        assertThat(report.issues()).filteredOn(issue -> issue.playerId() != null
                        && issue.playerId() == missing.playerId())
                .extracting(CatalogQualityIssue::code).contains("MISSING_CLUB");
        assertBlockedWith(report, "INVALID_CLUB");
    }

    @Test
    void competitionClubWithoutPlayersBlocksCatalog() {
        TestCatalog catalog = createCatalog("2097/2098", 2097);
        createPlayer(catalog, "Valido", "P", true, true);
        long emptyClub = createClub("Club Vuoto 2097");
        addMembership(catalog.seasonId(), emptyClub);

        assertBlockedWith(service.evaluateCatalogQuality(catalog.season()), "EMPTY_CLUB_ROSTER");
    }

    @Test
    void warningsAloneKeepCatalogReady() {
        TestCatalog catalog = createCatalog("2098/2099", 2098);
        createPlayer(catalog, "Rosa ridotta", "A", true, true);

        CatalogReadinessService.QualityReport report = service.evaluateCatalogQuality(catalog.season());

        assertThat(report.warningCount()).isPositive();
        assertThat(report.errorCount()).isZero();
        assertThat(report.status()).isEqualTo("READY");
    }

    @Test
    void verifiedMappingPointingElsewhereBlocksCatalog() {
        TestCatalog catalog = createCatalog("2099/2100", 2099);
        PlayerIds first = createPlayer(catalog, "Primo", "C", true, true);
        PlayerIds second = createPlayer(catalog, "Secondo", "D", true, true);
        jdbcClient.sql("""
                INSERT INTO verified_source_identity_links (
                    fantacalcio_external_id, secondary_source, secondary_external_id,
                    verification_method, verified)
                VALUES (:fcId, 'GAZZETTA', :gaId, 'MANUAL_VERIFICATION', TRUE)
                """).param("fcId", first.fantacalcioExternalId())
                .param("gaId", second.gazzettaExternalId()).update();

        assertBlockedWith(service.evaluateCatalogQuality(catalog.season()), "VERIFIED_MAPPING_INCONSISTENCY");
    }

    @Test
    void historicalVerifiedMappingDoesNotCauseSourceCountMismatch() {
        TestCatalog catalog = createCatalog("2100/2101", 2100);
        PlayerIds player = createPlayer(catalog, "Mapping storico", "C", true, true);
        insertMapping(player.playerId(), "FANTAMARKT", "FM-HISTORICAL-" + player.playerId(),
                "Mapping storico");

        CatalogReadinessService.QualityReport report = service.evaluateCatalogQuality(catalog.season());

        assertThat(report.issues()).noneMatch(issue ->
                "RECONCILIATION_SOURCE_MISMATCH".equals(issue.code()));
        assertThat(report.status()).isEqualTo("READY");
    }

    @Test
    void confirmedIdentityMissingFromCatalogBlocksReadiness() {
        TestCatalog catalog = createCatalog("2101/2102", 2101);
        PlayerIds player = createPlayer(catalog, "IdentitÃ  risolta dopo il catalogo", "C", true, true);
        long fantamarktBatch = createBatch(catalog.seasonId(), "FANTAMARKT", 1);
        String fantamarktId = "FM-LATE-" + player.playerId();
        insertImportRow(catalog.fcBatch(), player.fantacalcioExternalId(), "IdentitÃ  tardiva");
        insertImportRow(fantamarktBatch, fantamarktId, "Nome Fantamarkt diverso");
        insertMapping(player.playerId(), "FANTAMARKT", fantamarktId, "Nome Fantamarkt diverso");
        jdbcClient.sql("""
                INSERT INTO verified_source_identity_links (
                    fantacalcio_external_id, secondary_source, secondary_external_id,
                    verification_method, verified)
                VALUES (:fcId, 'FANTAMARKT', :fmId, 'MANUAL_VERIFICATION', TRUE)
                """).param("fcId", player.fantacalcioExternalId()).param("fmId", fantamarktId).update();
        jdbcClient.sql("DELETE FROM player_reconciliation_results WHERE player_id = :playerId")
                .param("playerId", player.playerId()).update();

        CatalogReadinessResponse response = service.evaluate(
                catalog.season(), catalog.fcBatch(), catalog.gaBatch(), fantamarktBatch);

        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(response.issues()).filteredOn(issue ->
                        "RECONCILED_PLAYER_NOT_OPERATIONAL".equals(issue.code()))
                .singleElement().satisfies(issue -> {
                    assertThat(issue.playerId()).isEqualTo(player.playerId());
                    assertThat(issue.fantacalcioExternalId()).isEqualTo(player.fantacalcioExternalId());
                    assertThat(issue.details()).containsEntry("previewSourcesCount", 2);
                });
    }

    @Test
    void confirmedExcludedIdentityDoesNotProduceCatalogGapError() {
        TestCatalog catalog = createCatalog("2102/2103", 2102);
        PlayerIds player = createPlayer(catalog, "IdentitÃ  esclusa", "D", true, true);
        long fantamarktBatch = createBatch(catalog.seasonId(), "FANTAMARKT", 1);
        String fantamarktId = "FM-EXCLUDED-" + player.playerId();
        insertImportRow(catalog.fcBatch(), player.fantacalcioExternalId(), "IdentitÃ  esclusa");
        insertImportRow(fantamarktBatch, fantamarktId, "IdentitÃ  esclusa Fantamarkt");
        insertMapping(player.playerId(), "FANTAMARKT", fantamarktId, "IdentitÃ  esclusa Fantamarkt");
        jdbcClient.sql("""
                INSERT INTO verified_source_identity_links (
                    fantacalcio_external_id, secondary_source, secondary_external_id,
                    verification_method, verified)
                VALUES (:fcId, 'FANTAMARKT', :fmId, 'MANUAL_VERIFICATION', TRUE)
                """).param("fcId", player.fantacalcioExternalId()).param("fmId", fantamarktId).update();
        insertReview(catalog, player.fantacalcioExternalId(), "IdentitÃ  esclusa", "EXCLUDED");
        jdbcClient.sql("DELETE FROM player_reconciliation_results WHERE player_id = :playerId")
                .param("playerId", player.playerId()).update();

        CatalogReadinessResponse response = service.evaluate(
                catalog.season(), catalog.fcBatch(), catalog.gaBatch(), fantamarktBatch);

        assertThat(response.issues()).noneMatch(issue ->
                "RECONCILED_PLAYER_NOT_OPERATIONAL".equals(issue.code()));
    }

    @Test
    void existingReadinessResponseKeepsLegacyFieldsAndUsesReadyBlockedStatus() {
        long fc = createBatch(seasonId("2026/2027"), "FANTACALCIO", 1);
        long ga = createBatch(seasonId("2026/2027"), "GAZZETTA", 1);
        insertImportRow(fc, "FC-QUALITY", "Quality Player");
        insertImportRow(ga, "GA-QUALITY", "Quality Player");

        CatalogReadinessResponse response = service.evaluate("2026/2027", fc, ga, null);

        assertThat(response.totalFantacalcioPlayers()).isEqualTo(1);
        assertThat(response.confirmedPlayers()).isEqualTo(1);
        assertThat(response.qualityPercentage()).isEqualByComparingTo("100.00");
        assertThat(response.status()).isIn("READY", "BLOCKED");
        assertThat(response.readinessStatus()).isEqualTo(response.status());
        assertThat(response.errorCount()).isEqualTo(response.issues().stream()
                .filter(issue -> "ERROR".equals(issue.severity())).count());
    }

    private void assertBlockedWith(CatalogReadinessService.QualityReport report, String code) {
        assertThat(report.status()).isEqualTo("BLOCKED");
        assertThat(report.issues()).extracting(CatalogQualityIssue::code).contains(code);
    }

    private TestCatalog createCatalog(String season, int startYear) {
        long seasonId = jdbcClient.sql("""
                INSERT INTO seasons (code, start_year, end_year, current_season)
                VALUES (:season, :startYear, :endYear, FALSE) RETURNING id
                """).param("season", season).param("startYear", startYear).param("endYear", startYear + 1)
                .query(Long.class).single();
        long clubId = createClub("Club " + startYear);
        addMembership(seasonId, clubId);
        long fcBatch = createBatch(seasonId, "FANTACALCIO", 0);
        long gaBatch = createBatch(seasonId, "GAZZETTA", 0);
        return new TestCatalog(season, seasonId, clubId, fcBatch, gaBatch);
    }

    private long createClub(String name) {
        return jdbcClient.sql("""
                INSERT INTO clubs (name, short_name, slug, country_code, active)
                VALUES (:name, :name, :slug, 'IT', TRUE) RETURNING id
                """).param("name", name).param("slug", name.toLowerCase().replace(' ', '-'))
                .query(Long.class).single();
    }

    private void addMembership(long seasonId, long clubId) {
        jdbcClient.sql("INSERT INTO season_clubs (season_id, club_id) VALUES (:seasonId, :clubId)")
                .param("seasonId", seasonId).param("clubId", clubId).update();
    }

    private PlayerIds createPlayer(TestCatalog catalog, String name, String role,
            boolean secondarySource, boolean registration) {
        long playerId = jdbcClient.sql("""
                INSERT INTO players (last_name, display_name, normalized_name, active)
                VALUES (:name, :name, :normalized, TRUE) RETURNING id
                """).param("name", name).param("normalized", name.toLowerCase())
                .query(Long.class).single();
        if (registration) {
            jdbcClient.sql("""
                    INSERT INTO player_registrations (player_id, club_id, season_id, registration_status)
                    VALUES (:playerId, :clubId, :seasonId, 'ACTIVE')
                    """).param("playerId", playerId).param("clubId", catalog.clubId())
                    .param("seasonId", catalog.seasonId()).update();
        }
        jdbcClient.sql("""
                INSERT INTO player_roles (player_id, season_id, source, fantasy_role, primary_role)
                VALUES (:playerId, :seasonId, 'FANTACALCIO', :role, TRUE)
                """).param("playerId", playerId).param("seasonId", catalog.seasonId())
                .param("role", role).update();
        String fcId = "FC-QG-" + playerId;
        String gaId = "GA-QG-" + playerId;
        insertMapping(playerId, "FANTACALCIO", fcId, name);
        if (secondarySource) {
            insertMapping(playerId, "GAZZETTA", gaId, name);
        }
        jdbcClient.sql("""
                INSERT INTO player_reconciliation_results (
                    season_id, player_id, normalized_name, normalized_club_name, sources_count,
                    identity_resolution_method, club_resolution_source)
                VALUES (:seasonId, :playerId, :normalized, :club, :sources, 'EXTERNAL_ID', 'FANTACALCIO')
                """).param("seasonId", catalog.seasonId()).param("playerId", playerId)
                .param("normalized", name.toLowerCase()).param("club", "club")
                .param("sources", secondarySource ? 2 : 1).update();
        return new PlayerIds(playerId, fcId, gaId);
    }

    private void insertMapping(long playerId, String source, String externalId, String name) {
        jdbcClient.sql("""
                INSERT INTO player_source_mappings (player_id, source, external_id, external_name, verified)
                VALUES (:playerId, :source, :externalId, :name, TRUE)
                """).param("playerId", playerId).param("source", source)
                .param("externalId", externalId).param("name", name).update();
    }

    private void insertReview(TestCatalog catalog, String externalId, String name, String status) {
        jdbcClient.sql("""
                INSERT INTO reconciliation_review_items (
                    season_id, fantacalcio_batch_id, gazzetta_batch_id,
                    fantacalcio_external_id, fantacalcio_name, fantacalcio_club,
                    review_status, exclusion_reason, excluded_at)
                VALUES (:seasonId, :fcBatch, :gaBatch, :externalId, :name, 'Test Club', :status,
                        CASE WHEN :status = 'EXCLUDED' THEN 'OUT_OF_SERIE_A' END,
                        CASE WHEN :status = 'EXCLUDED' THEN CURRENT_TIMESTAMP END)
                """).param("seasonId", catalog.seasonId()).param("fcBatch", catalog.fcBatch())
                .param("gaBatch", catalog.gaBatch()).param("externalId", externalId)
                .param("name", name).param("status", status).update();
    }

    private long createBatch(long seasonId, String source, int rows) {
        return jdbcClient.sql("""
                INSERT INTO import_batches (source, season_id, original_filename, status, total_rows,
                    valid_rows, invalid_rows, completed_at, source_observed_at)
                VALUES (:source, :seasonId, :filename, 'COMPLETED', :rows, :rows, 0,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id
                """).param("source", source).param("seasonId", seasonId)
                .param("filename", "quality-gate-" + source + ".csv").param("rows", rows)
                .query(Long.class).single();
    }

    private long seasonId(String season) {
        return jdbcClient.sql("SELECT id FROM seasons WHERE code = :season")
                .param("season", season).query(Long.class).single();
    }

    private void insertImportRow(long batchId, String externalId, String name) {
        jdbcClient.sql("""
                INSERT INTO player_import_rows (batch_id, row_number, external_id, external_name,
                    normalized_name, club_name, normalized_club_name, fantasy_role, valid, raw_data)
                VALUES (:batchId, 1, :externalId, :name, :normalized, 'Roma', 'roma', 'C', TRUE, '{}'::jsonb)
                """).param("batchId", batchId).param("externalId", externalId)
                .param("name", name).param("normalized", name.toLowerCase()).update();
    }

    private record TestCatalog(String season, long seasonId, long clubId, long fcBatch, long gaBatch) {
    }

    private record PlayerIds(long playerId, String fantacalcioExternalId, String gazzettaExternalId) {
    }
}
