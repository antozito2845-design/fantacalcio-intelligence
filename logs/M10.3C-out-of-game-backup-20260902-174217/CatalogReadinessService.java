package it.fantacalciointelligence.backend.catalog.readiness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import it.fantacalciointelligence.backend.common.ResourceNotFoundException;
import it.fantacalciointelligence.backend.freshness.DataFreshnessResponse;
import it.fantacalciointelligence.backend.freshness.DataFreshnessService;
import it.fantacalciointelligence.backend.reconciliation.preview.ReconciliationPreviewResponse;
import it.fantacalciointelligence.backend.reconciliation.preview.ReconciliationPreviewPlayer;
import it.fantacalciointelligence.backend.reconciliation.preview.ReconciliationPreviewService;

@Service
public class CatalogReadinessService {

    private static final Set<String> VALID_ROLES = Set.of("P", "D", "C", "A");
    private static final int SUSPICIOUS_MIN_TOTAL = 18;
    private static final int SUSPICIOUS_MIN_GOALKEEPERS = 2;
    private static final int SUSPICIOUS_MAX_ROLE_SHARE_PERCENT = 70;

    private final JdbcClient jdbcClient;
    private final ReconciliationPreviewService previewService;
    private final DataFreshnessService freshnessService;

    public CatalogReadinessService(
            JdbcClient jdbcClient,
            ReconciliationPreviewService previewService,
            DataFreshnessService freshnessService
    ) {
        this.jdbcClient = jdbcClient;
        this.previewService = previewService;
        this.freshnessService = freshnessService;
    }

    @Transactional(readOnly = true)
    public CatalogReadinessResponse evaluateLatest(String season) {
        long fantacalcioBatchId = requireLatestBatch(season, "FANTACALCIO");
        long gazzettaBatchId = requireLatestBatch(season, "GAZZETTA");
        Long fantamarktBatchId = optionalLatestBatch(season, "FANTAMARKT");
        return evaluate(season, fantacalcioBatchId, gazzettaBatchId, fantamarktBatchId);
    }

    @Transactional(readOnly = true)
    public CatalogReadinessResponse evaluate(
            String season,
            long fantacalcioBatchId,
            long gazzettaBatchId,
            Long fantamarktBatchId
    ) {
        ReconciliationPreviewResponse preview = previewService.preview(
                fantacalcioBatchId, gazzettaBatchId, fantamarktBatchId, false);
        DataFreshnessResponse freshness = freshnessService.evaluate(season);
        QualityReport qualityReport = evaluateCatalogQuality(season);

        List<CatalogQualityIssue> issues = new ArrayList<>(qualityReport.issues());
        addReconciledNotOperationalIssues(
                season, fantacalcioBatchId, preview, qualityReport, issues);
        freshness.blockers().forEach(message -> issues.add(issue(
                "ERROR", "DATA_FRESHNESS_BLOCKER", message, null, null, null, null, Map.of())));
        freshness.warnings().forEach(message -> issues.add(issue(
                "WARNING", "DATA_FRESHNESS_WARNING", message, null, null, null, null, Map.of())));

        if (preview.totalFantacalcioPlayers() == 0) {
            issues.add(issue("ERROR", "EMPTY_FANTACALCIO_BATCH",
                    "Il batch Fantacalcio non contiene giocatori", null, null, null, null, Map.of()));
        }
        if (preview.unresolvedPlayers() > 0) {
            issues.add(issue("WARNING", "UNRESOLVED_PREVIEW_IDENTITIES",
                    preview.unresolvedPlayers() + " giocatori Fantacalcio non sono confermati nella preview",
                    null, null, null, null, Map.of("count", preview.unresolvedPlayers())));
        }

        int pendingTransfers = countPendingTransfers(season);
        int officialExits = countOfficialExits(season);
        int missingFromFantacalcio = countMissingFromFantacalcio(season, fantacalcioBatchId);
        if (pendingTransfers > 0) {
            issues.add(issue("WARNING", "PENDING_TRANSFER_CLAIMS",
                    pendingTransfers + " trasferimenti richiedono verifica", null, null, null, null,
                    Map.of("count", pendingTransfers)));
        }
        if (missingFromFantacalcio > 0) {
            issues.add(issue("WARNING", "MISSING_FROM_LATEST_FANTACALCIO_SNAPSHOT",
                    missingFromFantacalcio + " giocatori operativi non compaiono nell'ultimo snapshot Fantacalcio",
                    null, null, null, null, Map.of("count", missingFromFantacalcio)));
        }

        List<CatalogQualityIssue> immutableIssues = List.copyOf(issues);
        int errorCount = countSeverity(immutableIssues, "ERROR");
        int warningCount = countSeverity(immutableIssues, "WARNING");
        String status = errorCount > 0 ? "BLOCKED" : "READY";
        List<String> blockers = messages(immutableIssues, "ERROR");
        List<String> warnings = messages(immutableIssues, "WARNING");

        return new CatalogReadinessResponse(
                season, fantacalcioBatchId, gazzettaBatchId, fantamarktBatchId,
                preview.totalFantacalcioPlayers(), preview.confirmedPlayers(), preview.unresolvedPlayers(),
                qualityReport.totalOperationalPlayers(), qualityReport.playersWithoutFantacalcioRole(),
                qualityReport.playersWithoutActiveRegistration(), pendingTransfers, officialExits,
                missingFromFantacalcio, calculateQuality(preview.confirmedPlayers(), preview.totalFantacalcioPlayers()),
                freshness, status, blockers, warnings, status, qualityReport.totalOperationalPlayers(),
                qualityReport.clubSummaries().size(), qualityReport.playersByRole(), errorCount, warningCount,
                immutableIssues, qualityReport.clubSummaries());
    }

    QualityReport evaluateCatalogQuality(String season) {
        requireSeason(season);
        List<CatalogQualityIssue> issues = new ArrayList<>();
        List<OperationalPlayer> players = loadOperationalPlayers(season);

        for (OperationalPlayer player : players) {
            validatePlayer(player, issues);
        }
        addDuplicateIssues(players, issues);
        addOpenReviewIssues(season, issues);
        addExcludedCatalogIssues(season, issues);
        addVerifiedLinkIssues(season, issues);

        List<CatalogClubSummary> clubs = loadClubSummaries(season);
        addClubIssues(clubs, issues);
        CatalogRoleSummary roles = summarizeRoles(players);

        return new QualityReport(
                players.size(),
                (int) players.stream().filter(player -> player.role() == null || player.role().isBlank()).count(),
                (int) players.stream().filter(player -> player.registrationId() == null).count(),
                roles,
                players.stream().map(OperationalPlayer::fantacalcioExternalId)
                        .filter(id -> id != null && !id.isBlank())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                List.copyOf(clubs), List.copyOf(issues));
    }

    private void validatePlayer(OperationalPlayer player, List<CatalogQualityIssue> issues) {
        if (player.playerId() == null) {
            issues.add(playerIssue("MISSING_PLAYER_ID", "Giocatore operativo senza ID", player));
        }
        if (player.name() == null || player.name().isBlank()) {
            issues.add(playerIssue("MISSING_PLAYER_NAME", "Giocatore operativo senza nome", player));
        }
        if (player.registrationId() == null || player.clubId() == null || player.club() == null) {
            issues.add(playerIssue("MISSING_CLUB", "Giocatore operativo senza club per la stagione", player));
        } else if (!player.clubActive()) {
            issues.add(playerIssue("INVALID_CLUB", "Giocatore associato a un club non attivo", player));
        }
        if (player.role() == null || player.role().isBlank()) {
            issues.add(playerIssue("MISSING_FANTACALCIO_ROLE", "Ruolo Fantacalcio mancante", player));
        } else if (!VALID_ROLES.contains(player.role())) {
            issues.add(playerIssue("INVALID_FANTACALCIO_ROLE", "Ruolo Fantacalcio non valido: " + player.role(), player));
        }
        if (player.fantacalcioExternalId() == null || player.fantacalcioExternalId().isBlank()) {
            issues.add(playerIssue("MISSING_FANTACALCIO_SOURCE", "Fonte identitaria Fantacalcio mancante", player));
        }
        /*
         * Fantacalcio is sufficient for operational inclusion.
         *
         * Secondary identity sources are enrichment/confirmation.
         * A Fantacalcio-only player is therefore valid while
         * enrichment is pending.
         */

        /*
         * sources_count belongs to the latest reconciliation
         * snapshot, while player_source_mappings is persistent
         * identity history.
         *
         * Current snapshot sources may legitimately be fewer than
         * persistent verified mappings.
         *
         * Invalid cases are:
         * - no source at all;
         * - reconciliation claiming more sources than the verified
         *   persistent identity mappings.
         */
        if (player.reconciledSources() < 1
                || player.reconciledSources() > player.identitySources()) {

            issues.add(issue(
                    "ERROR",
                    "RECONCILIATION_SOURCE_MISMATCH",
                    "Conteggio fonti della reconciliation incoerente con i mapping verificati",
                    player.playerId(),
                    player.fantacalcioExternalId(),
                    player.name(),
                    player.club(),
                    Map.of(
                            "reconciliationSources",
                            player.reconciledSources(),
                            "verifiedIdentitySources",
                            player.identitySources()
                    )
            ));
        }
    }

    private void addDuplicateIssues(List<OperationalPlayer> players, List<CatalogQualityIssue> issues) {
        Map<String, List<OperationalPlayer>> byFantacalcioId = new LinkedHashMap<>();
        players.stream()
                .filter(player -> player.fantacalcioExternalId() != null && !player.fantacalcioExternalId().isBlank())
                .forEach(player -> byFantacalcioId.computeIfAbsent(
                        player.fantacalcioExternalId(), ignored -> new ArrayList<>()).add(player));
        byFantacalcioId.values().stream().filter(group -> group.size() > 1).forEach(group ->
                group.forEach(player -> issues.add(issue("ERROR", "DUPLICATE_OPERATIONAL_PLAYER",
                        "IdentitÃ  Fantacalcio duplicata nel catalogo operativo", player.playerId(),
                        player.fantacalcioExternalId(), player.name(), player.club(),
                        Map.of("duplicateCount", group.size())))));
    }

    private void addOpenReviewIssues(String season, List<CatalogQualityIssue> issues) {
        jdbcClient.sql("""
                SELECT review.id, review.fantacalcio_external_id, review.fantacalcio_name,
                       review.fantacalcio_club
                FROM reconciliation_review_items review
                JOIN seasons season ON season.id = review.season_id
                WHERE season.code = :season AND review.review_status = 'OPEN'
                ORDER BY review.id
                """).param("season", season).query((rs, rowNum) -> issue(
                        "WARNING", "OPEN_RECONCILIATION_REVIEW", "Review di reconciliation aperta: arricchimento identita secondario non completato",
                        null, rs.getString("fantacalcio_external_id"), rs.getString("fantacalcio_name"),
                        rs.getString("fantacalcio_club"), Map.of("reviewId", rs.getLong("id"))))
                .list().forEach(issues::add);
    }

    private void addExcludedCatalogIssues(String season, List<CatalogQualityIssue> issues) {
        jdbcClient.sql("""
                SELECT review.id, review.fantacalcio_external_id, review.fantacalcio_name,
                       review.fantacalcio_club, mapping.player_id
                FROM reconciliation_review_items review
                JOIN seasons season ON season.id = review.season_id
                JOIN player_source_mappings mapping
                  ON mapping.source = 'FANTACALCIO'
                 AND mapping.external_id = review.fantacalcio_external_id
                JOIN player_reconciliation_results result
                  ON result.player_id = mapping.player_id AND result.season_id = review.season_id
                WHERE season.code = :season AND review.review_status = 'EXCLUDED'
                ORDER BY review.id
                """).param("season", season).query((rs, rowNum) -> issue(
                        "ERROR", "EXCLUDED_PLAYER_IN_CATALOG", "Giocatore EXCLUDED presente nel catalogo operativo",
                        rs.getLong("player_id"), rs.getString("fantacalcio_external_id"),
                        rs.getString("fantacalcio_name"), rs.getString("fantacalcio_club"),
                        Map.of("reviewId", rs.getLong("id"))))
                .list().forEach(issues::add);
    }

    private void addVerifiedLinkIssues(String season, List<CatalogQualityIssue> issues) {
        jdbcClient.sql("""
                SELECT fc.player_id, fc.external_id AS fc_id, player.display_name,
                       link.secondary_source, link.secondary_external_id,
                       secondary.player_id AS secondary_player_id
                FROM verified_source_identity_links link
                JOIN player_source_mappings fc
                  ON fc.source = 'FANTACALCIO' AND fc.external_id = link.fantacalcio_external_id
                JOIN player_reconciliation_results result ON result.player_id = fc.player_id
                JOIN seasons season ON season.id = result.season_id
                JOIN players player ON player.id = fc.player_id
                LEFT JOIN player_source_mappings secondary
                  ON secondary.source = link.secondary_source
                 AND secondary.external_id = link.secondary_external_id
                WHERE season.code = :season AND link.verified = TRUE
                  AND (secondary.player_id IS NULL OR secondary.player_id <> fc.player_id)
                """).param("season", season).query((rs, rowNum) -> issue(
                        "ERROR", "VERIFIED_MAPPING_INCONSISTENCY",
                        "Un mapping verificato non Ã¨ rispettato dal catalogo operativo",
                        rs.getLong("player_id"), rs.getString("fc_id"), rs.getString("display_name"), null,
                        Map.of("secondarySource", rs.getString("secondary_source"),
                                "secondaryExternalId", rs.getString("secondary_external_id"))))
                .list().forEach(issues::add);
    }

    private void addReconciledNotOperationalIssues(
            String season,
            long fantacalcioBatchId,
            ReconciliationPreviewResponse preview,
            QualityReport qualityReport,
            List<CatalogQualityIssue> issues
    ) {
        preview.players().stream()
                .filter(player -> "CONFIRMED".equals(player.status()))
                .filter(player -> !qualityReport.operationalFantacalcioIds()
                        .contains(player.fantacalcioExternalId()))
                .filter(player -> !isExcluded(season, player.fantacalcioExternalId()))
                .filter(player -> !isCurrentOfficialExit(
                        season, fantacalcioBatchId, player.fantacalcioExternalId()))
                .map(player -> reconciledNotOperationalIssue(season, player))
                .forEach(issues::add);
    }

    private boolean isExcluded(String season, String fantacalcioExternalId) {
        return jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM reconciliation_review_items review
                    JOIN seasons season ON season.id = review.season_id
                    WHERE season.code = :season
                      AND review.fantacalcio_external_id = :externalId
                      AND review.review_status = 'EXCLUDED')
                """).param("season", season).param("externalId", fantacalcioExternalId)
                .query(Boolean.class).single();
    }

    private boolean isCurrentOfficialExit(
            String season,
            long fantacalcioBatchId,
            String fantacalcioExternalId
    ) {
        return jdbcClient.sql("""
                WITH latest_claim AS (
                    SELECT claim.to_club_id,
                           COALESCE(claim.source_published_at, claim.verified_at, claim.observed_at)
                               AS official_at
                    FROM player_source_mappings mapping
                    JOIN player_transfer_claims claim ON claim.player_id = mapping.player_id
                    JOIN seasons season ON season.id = claim.season_id
                    WHERE mapping.source = 'FANTACALCIO'
                      AND mapping.external_id = :externalId
                      AND season.code = :season
                      AND claim.verification_status = 'OFFICIAL'
                      AND claim.source IN ('FIGC', 'LEGA_SERIE_A', 'OFFICIAL_CLUB')
                    ORDER BY COALESCE(
                            claim.source_published_at, claim.verified_at, claim.observed_at) DESC,
                             claim.id DESC
                    LIMIT 1)
                SELECT EXISTS (
                    SELECT 1 FROM latest_claim claim
                    JOIN import_batches batch ON batch.id = :batchId
                    WHERE claim.to_club_id IS NULL
                      AND NOT (batch.source_observed_at IS NOT NULL
                               AND claim.official_at IS NOT NULL
                               AND claim.official_at < batch.source_observed_at))
                """).param("externalId", fantacalcioExternalId).param("season", season)
                .param("batchId", fantacalcioBatchId).query(Boolean.class).single();
    }

    private CatalogQualityIssue reconciledNotOperationalIssue(
            String season,
            ReconciliationPreviewPlayer previewPlayer
    ) {
        ReconciledPlayerState state = loadReconciledPlayerState(
                season, previewPlayer.fantacalcioExternalId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previewSourcesCount", previewPlayer.sourcesCount());
        putIfNotNull(details, "identityResolutionMethod", previewPlayer.identityResolutionMethod());
        putIfNotNull(details, "playerActive", state.active());
        putIfNotNull(details, "registrationStatus", state.registrationStatus());
        putIfNotNull(details, "fantacalcioRole", state.role());
        putIfNotNull(details, "reviewStatus", state.reviewStatus());
        putIfNotNull(details, "sourceMappings", state.mappings());

        return issue("ERROR", "RECONCILED_PLAYER_NOT_OPERATIONAL",
                "IdentitÃ  confermata dalla reconciliation corrente ma assente dal catalogo operativo",
                state.playerId(), previewPlayer.fantacalcioExternalId(),
                state.playerName() != null ? state.playerName() : previewPlayer.fantacalcioName(),
                state.club() != null ? state.club() : previewPlayer.fantacalcioClub(), details);
    }

    private ReconciledPlayerState loadReconciledPlayerState(
            String season,
            String fantacalcioExternalId
    ) {
        return jdbcClient.sql("""
                SELECT player.id AS player_id, player.display_name, player.active,
                       registration.registration_status, club.name AS club_name,
                       role.fantasy_role, review.review_status,
                       STRING_AGG(DISTINCT source_mapping.source || ':' ||
                           COALESCE(source_mapping.external_id, '<null>'), ', ') AS mappings
                FROM player_source_mappings mapping
                JOIN players player ON player.id = mapping.player_id
                JOIN seasons season ON season.code = :season
                LEFT JOIN player_registrations registration
                  ON registration.player_id = player.id AND registration.season_id = season.id
                LEFT JOIN clubs club ON club.id = registration.club_id
                LEFT JOIN player_roles role
                  ON role.player_id = player.id AND role.season_id = season.id
                 AND role.source = 'FANTACALCIO'
                LEFT JOIN reconciliation_review_items review
                  ON review.season_id = season.id
                 AND review.fantacalcio_external_id = mapping.external_id
                LEFT JOIN player_source_mappings source_mapping ON source_mapping.player_id = player.id
                WHERE mapping.source = 'FANTACALCIO' AND mapping.external_id = :externalId
                GROUP BY player.id, player.display_name, player.active,
                         registration.registration_status, club.name,
                         role.fantasy_role, review.review_status
                """).param("season", season).param("externalId", fantacalcioExternalId)
                .query((rs, rowNum) -> new ReconciledPlayerState(
                        rs.getObject("player_id", Long.class), rs.getString("display_name"),
                        rs.getObject("active", Boolean.class), rs.getString("registration_status"),
                        rs.getString("club_name"), rs.getString("fantasy_role"),
                        rs.getString("review_status"), rs.getString("mappings")))
                .optional().orElse(new ReconciledPlayerState(
                        null, null, null, null, null, null, null, null));
    }

    private void putIfNotNull(Map<String, Object> details, String key, Object value) {
        if (value != null) {
            details.put(key, value);
        }
    }

    private void addClubIssues(List<CatalogClubSummary> clubs, List<CatalogQualityIssue> issues) {
        for (CatalogClubSummary club : clubs) {
            if (club.totalPlayers() == 0) {
                issues.add(issue("ERROR", "EMPTY_CLUB_ROSTER", "Club della competizione senza giocatori operativi",
                        null, null, null, club.club(), Map.of("clubId", club.clubId())));
                continue;
            }
            if (club.totalPlayers() < SUSPICIOUS_MIN_TOTAL || club.P() < SUSPICIOUS_MIN_GOALKEEPERS) {
                issues.add(issue("WARNING", "SUSPICIOUS_CLUB_ROSTER", "Rosa insolitamente ridotta",
                        null, null, null, club.club(), Map.of("totalPlayers", club.totalPlayers(), "goalkeepers", club.P())));
            }
            int largestRole = Math.max(Math.max(club.P(), club.D()), Math.max(club.C(), club.A()));
            if (largestRole * 100 > club.totalPlayers() * SUSPICIOUS_MAX_ROLE_SHARE_PERCENT) {
                issues.add(issue("WARNING", "SUSPICIOUS_ROLE_DISTRIBUTION", "Distribuzione ruoli insolitamente sbilanciata",
                        null, null, null, club.club(), Map.of("largestRoleSharePercent",
                                largestRole * 100 / club.totalPlayers())));
            }
        }
    }

    private List<OperationalPlayer> loadOperationalPlayers(String season) {
        return jdbcClient.sql("""
                SELECT result.player_id, player.display_name, registration.id AS registration_id,
                       registration.club_id, club.name AS club_name,
                       (season_club.club_id IS NOT NULL) AS club_active,
                       role.fantasy_role,
                       MAX(mapping.external_id) FILTER (WHERE mapping.source = 'FANTACALCIO' AND mapping.verified = TRUE)
                           AS fantacalcio_external_id,
                       COUNT(DISTINCT mapping.source) FILTER (
                           WHERE mapping.verified = TRUE AND mapping.source IN ('FANTACALCIO', 'GAZZETTA', 'FANTAMARKT'))
                           AS identity_sources,
                       result.sources_count
                FROM player_reconciliation_results result
                JOIN seasons season ON season.id = result.season_id
                LEFT JOIN players player ON player.id = result.player_id
                LEFT JOIN player_registrations registration
                  ON registration.player_id = result.player_id AND registration.season_id = result.season_id
                 AND registration.registration_status = 'ACTIVE'
                LEFT JOIN clubs club ON club.id = registration.club_id
                LEFT JOIN season_clubs season_club
                  ON season_club.season_id = result.season_id AND season_club.club_id = registration.club_id
                LEFT JOIN player_roles role
                  ON role.player_id = result.player_id AND role.season_id = result.season_id
                 AND role.source = 'FANTACALCIO'
                LEFT JOIN player_source_mappings mapping ON mapping.player_id = result.player_id
                WHERE season.code = :season AND COALESCE(player.active, FALSE) = TRUE
                GROUP BY result.player_id, player.display_name, registration.id, registration.club_id,
                         club.name, season_club.club_id, role.fantasy_role, result.sources_count
                ORDER BY result.player_id
                """).param("season", season).query((rs, rowNum) -> new OperationalPlayer(
                        rs.getObject("player_id", Long.class), rs.getString("display_name"),
                        rs.getObject("registration_id", Long.class), rs.getObject("club_id", Long.class),
                        rs.getString("club_name"), rs.getBoolean("club_active"), rs.getString("fantasy_role"),
                        rs.getString("fantacalcio_external_id"), rs.getInt("identity_sources"),
                        rs.getInt("sources_count"))).list();
    }

    private List<CatalogClubSummary> loadClubSummaries(String season) {
        return jdbcClient.sql("""
                SELECT club.id, club.name,
                       COUNT(player.id) AS total_players,
                       COUNT(player.id) FILTER (WHERE role.fantasy_role = 'P') AS p,
                       COUNT(player.id) FILTER (WHERE role.fantasy_role = 'D') AS d,
                       COUNT(player.id) FILTER (WHERE role.fantasy_role = 'C') AS c,
                       COUNT(player.id) FILTER (WHERE role.fantasy_role = 'A') AS a
                FROM seasons season
                JOIN season_clubs season_club ON season_club.season_id = season.id
                JOIN clubs club ON club.id = season_club.club_id
                LEFT JOIN player_registrations registration
                  ON registration.club_id = club.id AND registration.season_id = season.id
                 AND registration.registration_status = 'ACTIVE'
                LEFT JOIN player_reconciliation_results result
                  ON result.player_id = registration.player_id AND result.season_id = season.id
                LEFT JOIN players player ON player.id = result.player_id AND player.active = TRUE
                LEFT JOIN player_roles role
                  ON role.player_id = result.player_id AND role.season_id = season.id AND role.source = 'FANTACALCIO'
                WHERE season.code = :season
                GROUP BY club.id, club.name
                ORDER BY club.name
                """).param("season", season).query((rs, rowNum) -> new CatalogClubSummary(
                        rs.getLong("id"), rs.getString("name"), rs.getInt("total_players"),
                        rs.getInt("p"), rs.getInt("d"), rs.getInt("c"), rs.getInt("a"))).list();
    }

    private CatalogRoleSummary summarizeRoles(List<OperationalPlayer> players) {
        return new CatalogRoleSummary(countRole(players, "P"), countRole(players, "D"),
                countRole(players, "C"), countRole(players, "A"));
    }

    private int countRole(List<OperationalPlayer> players, String role) {
        return (int) players.stream().filter(player -> role.equals(player.role())).count();
    }

    private CatalogQualityIssue playerIssue(String code, String message, OperationalPlayer player) {
        return issue("ERROR", code, message, player.playerId(), player.fantacalcioExternalId(),
                player.name(), player.club(), Map.of());
    }

    private CatalogQualityIssue issue(String severity, String code, String message, Long playerId,
            String fantacalcioExternalId, String playerName, String club, Map<String, Object> details) {
        return new CatalogQualityIssue(severity, code, message, playerId, fantacalcioExternalId,
                playerName, club, Map.copyOf(details));
    }

    private int countSeverity(List<CatalogQualityIssue> issues, String severity) {
        return (int) issues.stream().filter(issue -> severity.equals(issue.severity())).count();
    }

    private List<String> messages(List<CatalogQualityIssue> issues, String severity) {
        return issues.stream().filter(issue -> severity.equals(issue.severity()))
                .map(CatalogQualityIssue::message).toList();
    }

    private void requireSeason(String season) {
        boolean exists = jdbcClient.sql("SELECT EXISTS (SELECT 1 FROM seasons WHERE code = :season)")
                .param("season", season).query(Boolean.class).single();
        if (!exists) {
            throw new ResourceNotFoundException("Stagione non trovata: " + season);
        }
    }

    private int countPendingTransfers(String season) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM player_transfer_claims claim
                JOIN seasons season ON season.id = claim.season_id
                WHERE season.code = :season
                  AND claim.verification_status IN ('DETECTED', 'PENDING_VERIFICATION')
                """).param("season", season).query(Integer.class).single();
    }

    private int countOfficialExits(String season) {
        return jdbcClient.sql("""
                SELECT COUNT(*) FROM player_registration_events event
                JOIN seasons season ON season.id = event.season_id
                WHERE season.code = :season AND event.event_type = 'LEFT_SERIE_A'
                """).param("season", season).query(Integer.class).single();
    }

    private int countMissingFromFantacalcio(String season, long fantacalcioBatchId) {
        return jdbcClient.sql("""
                SELECT COUNT(DISTINCT result.player_id)
                FROM player_reconciliation_results result
                JOIN seasons season ON season.id = result.season_id
                JOIN players player ON player.id = result.player_id AND player.active = TRUE
                JOIN player_source_mappings mapping
                  ON mapping.player_id = player.id AND mapping.source = 'FANTACALCIO'
                 AND mapping.external_id IS NOT NULL AND mapping.verified = TRUE
                WHERE season.code = :season AND NOT EXISTS (
                    SELECT 1 FROM player_import_rows import_row
                    WHERE import_row.batch_id = :batchId AND import_row.valid = TRUE
                      AND import_row.external_id = mapping.external_id)
                """).param("season", season).param("batchId", fantacalcioBatchId)
                .query(Integer.class).single();
    }

    private BigDecimal calculateQuality(int confirmed, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(confirmed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private long requireLatestBatch(String season, String source) {
        Long id = optionalLatestBatch(season, source);
        if (id == null) {
            throw new ResourceNotFoundException("Nessun batch COMPLETED " + source + " per " + season);
        }
        return id;
    }

    private Long optionalLatestBatch(String season, String source) {
        return jdbcClient.sql("""
                SELECT batch.id FROM import_batches batch
                JOIN seasons season ON season.id = batch.season_id
                WHERE season.code = :season AND batch.source = :source AND batch.status = 'COMPLETED'
                  AND LOWER(batch.original_filename) NOT LIKE '%test%'
                ORDER BY COALESCE(batch.source_observed_at, batch.completed_at, batch.started_at) DESC,
                         batch.id DESC LIMIT 1
                """).param("season", season).param("source", source)
                .query(Long.class).optional().orElse(null);
    }

    record QualityReport(
            int totalOperationalPlayers,
            int playersWithoutFantacalcioRole,
            int playersWithoutActiveRegistration,
            CatalogRoleSummary playersByRole,
            Set<String> operationalFantacalcioIds,
            List<CatalogClubSummary> clubSummaries,
            List<CatalogQualityIssue> issues
    ) {
        int errorCount() {
            return (int) issues.stream().filter(issue -> "ERROR".equals(issue.severity())).count();
        }

        int warningCount() {
            return (int) issues.stream().filter(issue -> "WARNING".equals(issue.severity())).count();
        }

        String status() {
            return errorCount() == 0 ? "READY" : "BLOCKED";
        }
    }

    private record OperationalPlayer(
            Long playerId,
            String name,
            Long registrationId,
            Long clubId,
            String club,
            boolean clubActive,
            String role,
            String fantacalcioExternalId,
            int identitySources,
            int reconciledSources
    ) {
    }

    private record ReconciledPlayerState(
            Long playerId,
            String playerName,
            Boolean active,
            String registrationStatus,
            String club,
            String role,
            String reviewStatus,
            String mappings
    ) {
    }
}
