package it.fantacalciointelligence.backend.identity.enrichment;

import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class IdentityEnrichmentReadinessService {
    private final JdbcClient jdbcClient;

    public IdentityEnrichmentReadinessService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public IdentityEnrichmentReadinessResponse evaluate(String season) {
        Map<String, Object> row = jdbcClient.sql("""
                WITH operational AS (
                    SELECT DISTINCT p.id
                    FROM player_reconciliation_results result
                    JOIN seasons s ON s.id = result.season_id AND s.code = :season
                    JOIN players p ON p.id = result.player_id AND p.active = TRUE
                    JOIN player_registrations registration
                      ON registration.player_id = p.id AND registration.season_id = s.id
                     AND registration.registration_status = 'ACTIVE'
                )
                SELECT COUNT(*) AS operational, COUNT(ip.player_id) AS profiled,
                       COUNT(*) FILTER (WHERE ip.identity_status = 'VERIFIED_COMPLETE') AS complete,
                       COUNT(*) FILTER (WHERE ip.identity_status = 'VERIFIED_PARTIAL') AS partial,
                       COUNT(*) FILTER (WHERE ip.identity_status = 'NO_SERIE_A_HISTORY_EXPECTED') AS no_history,
                       COUNT(*) FILTER (WHERE ip.identity_status = 'REVIEW_REQUIRED') AS review,
                       COUNT(*) FILTER (WHERE ip.identity_status = 'DATA_SOURCE_MISSING' OR ip.player_id IS NULL) AS missing,
                       COUNT(*) FILTER (WHERE ip.canonical_full_name IS NOT NULL) AS names,
                       COUNT(*) FILTER (WHERE ip.birth_date IS NOT NULL) AS births,
                       COUNT(*) FILTER (WHERE ip.nationality IS NOT NULL) AS nationalities
                FROM operational op LEFT JOIN player_identity_profiles ip ON ip.player_id = op.id
                """).param("season", season).query().singleRow();
        int operational = number(row, "operational");
        int profiled = number(row, "profiled");
        int review = number(row, "review");
        int missing = number(row, "missing");
        String status = review == 0 && missing == 0 && profiled == operational
                ? "READY_FOR_HUMAN_REVIEW" : "BLOCKED_DATA_SOURCE";
        return new IdentityEnrichmentReadinessResponse(status, operational, profiled,
                number(row, "complete"), number(row, "partial"), number(row, "no_history"),
                review, missing, number(row, "names"), number(row, "births"), number(row, "nationalities"));
    }

    private int number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }
}
