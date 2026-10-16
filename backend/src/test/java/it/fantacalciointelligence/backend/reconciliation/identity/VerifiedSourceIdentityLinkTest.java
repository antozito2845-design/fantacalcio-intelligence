package it.fantacalciointelligence.backend.reconciliation.identity;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class VerifiedSourceIdentityLinkTest {

    @Autowired
    private PlayerIdentityResolutionService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void shouldResolveIdentityUsingStableExternalIdsWithoutPlayerId() {

        Long seasonId =
                jdbcClient.sql("""
                        SELECT id
                        FROM seasons
                        WHERE code = '2026/2027'
                        """)
                        .query(Long.class)
                        .single();

        long fantacalcioBatch =
                createBatch(
                        seasonId,
                        "FANTACALCIO",
                        "verified-link-fantacalcio-test.csv"
                );

        long gazzettaBatch =
                createBatch(
                        seasonId,
                        "GAZZETTA",
                        "verified-link-gazzetta-test.csv"
                );

        insertRow(
                fantacalcioBatch,
                "FC-STABLE-LINK-TEST",
                "Nome Fantacalcio Test",
                "nome fantacalcio test",
                "Roma"
        );

        insertRow(
                gazzettaBatch,
                "GA-STABLE-LINK-TEST",
                "Nome Gazzetta Diverso",
                "nome gazzetta diverso",
                "Roma"
        );

        jdbcClient.sql("""
                INSERT INTO verified_source_identity_links (
                    fantacalcio_external_id,
                    secondary_source,
                    secondary_external_id,
                    verification_method,
                    verified,
                    verification_note
                )
                VALUES (
                    'FC-STABLE-LINK-TEST',
                    'GAZZETTA',
                    'GA-STABLE-LINK-TEST',
                    'MANUAL_VERIFICATION',
                    TRUE,
                    'Automated integration test'
                )
                """)
                .update();

        List<ResolvedPlayerIdentity> result =
                service.resolve(
                        fantacalcioBatch,
                        gazzettaBatch,
                        null
                );

        assertThat(result)
                .hasSize(1);

        ResolvedPlayerIdentity identity =
                result.getFirst();

        assertThat(identity.sourcesCount())
                .isEqualTo(2);

        assertThat(identity.identityResolutionMethod())
                .isEqualTo("VERIFIED_SOURCE_LINK");

        assertThat(identity.gazzettaExternalId())
                .isEqualTo("GA-STABLE-LINK-TEST");

        assertThat(identity.gazzettaName())
                .isEqualTo("Nome Gazzetta Diverso");
    }

    private long createBatch(
            Long seasonId,
            String source,
            String filename
    ) {

        return jdbcClient.sql("""
                INSERT INTO import_batches (
                    source,
                    season_id,
                    original_filename,
                    status,
                    total_rows,
                    valid_rows,
                    invalid_rows,
                    completed_at
                )
                VALUES (
                    :source,
                    :seasonId,
                    :filename,
                    'COMPLETED',
                    1,
                    1,
                    0,
                    CURRENT_TIMESTAMP
                )
                RETURNING id
                """)
                .param("source", source)
                .param("seasonId", seasonId)
                .param("filename", filename)
                .query(Long.class)
                .single();
    }

    private void insertRow(
            long batchId,
            String externalId,
            String externalName,
            String normalizedName,
            String club
    ) {

        jdbcClient.sql("""
                INSERT INTO player_import_rows (
                    batch_id,
                    row_number,
                    external_id,
                    external_name,
                    normalized_name,
                    club_name,
                    normalized_club_name,
                    fantasy_role,
                    valid,
                    raw_data
                )
                VALUES (
                    :batchId,
                    1,
                    :externalId,
                    :externalName,
                    :normalizedName,
                    :club,
                    LOWER(:club),
                    'C',
                    TRUE,
                    CAST(:rawData AS JSONB)
                )
                """)
                .param("batchId", batchId)
                .param("externalId", externalId)
                .param("externalName", externalName)
                .param("normalizedName", normalizedName)
                .param("club", club)
                .param(
                        "rawData",
                        """
                        {
                          "verifiedSourceIdentityLinkTest": true
                        }
                        """
                )
                .update();
    }
}