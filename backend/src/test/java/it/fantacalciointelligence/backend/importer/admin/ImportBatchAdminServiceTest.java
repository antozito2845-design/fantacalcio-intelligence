package it.fantacalciointelligence.backend.importer.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ResourceNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ImportBatchAdminServiceTest {

    private static final String TEST_FILENAME =
            "admin-import-history-test.csv";

    @Autowired
    private ImportBatchAdminService service;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void cleanup() {
        jdbcClient.sql("""
                DELETE FROM import_batches
                WHERE original_filename = :filename
                """)
                .param(
                        "filename",
                        TEST_FILENAME
                )
                .update();
    }

    @Test
    void shouldFilterAndReturnImportBatches() {

        Long batchId = createBatch();

        ImportBatchPageResponse response =
                service.findBatches(
                        "fantacalcio",
                        "2024/2025",
                        "completed",
                        0,
                        20
                );

        assertThat(response.content())
                .extracting(
                        ImportBatchSummaryResponse::id
                )
                .contains(batchId);

        ImportBatchSummaryResponse batch =
                response.content()
                        .stream()
                        .filter(item ->
                                item.id().equals(batchId)
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(batch.source())
                .isEqualTo("FANTACALCIO");

        assertThat(batch.status())
                .isEqualTo("COMPLETED");

        assertThat(batch.totalRows())
                .isEqualTo(2);

        assertThat(batch.invalidRows())
                .isEqualTo(1);
    }

    @Test
    void shouldReturnBatchDetailAndInvalidRows() {

        Long batchId = createBatch();

        createInvalidRow(batchId);

        ImportBatchDetailResponse response =
                service.findBatch(
                        batchId,
                        0,
                        50
                );

        assertThat(response.batch().id())
                .isEqualTo(batchId);

        assertThat(response.totalInvalidRows())
                .isEqualTo(1);

        assertThat(response.invalidRows())
                .hasSize(1);

        assertThat(
                response.invalidRows()
                        .getFirst()
                        .validationError()
        ).contains(
                "Ruolo non valido"
        );
    }

    @Test
    void shouldRejectInvalidFiltersAndUnknownBatch() {

        assertThatThrownBy(() ->
                service.findBatches(
                        "FONTE_ERRATA",
                        null,
                        null,
                        0,
                        20
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Fonte non valida"
                );

        assertThatThrownBy(() ->
                service.findBatches(
                        null,
                        null,
                        "STATO_ERRATO",
                        0,
                        20
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Stato import non valido"
                );

        assertThatThrownBy(() ->
                service.findBatch(
                        Long.MAX_VALUE,
                        0,
                        50
                )
        )
                .isInstanceOf(
                        ResourceNotFoundException.class
                )
                .hasMessageContaining(
                        "Batch import non trovato"
                );
    }

    private Long createBatch() {
        return jdbcClient.sql("""
                INSERT INTO import_batches (
                    source,
                    season_id,
                    original_filename,
                    status,
                    total_rows,
                    valid_rows,
                    invalid_rows,
                    reconciled_rows,
                    completed_at
                )
                SELECT
                    'FANTACALCIO',
                    season.id,
                    :filename,
                    'COMPLETED',
                    2,
                    1,
                    1,
                    0,
                    CURRENT_TIMESTAMP
                FROM seasons season
                WHERE season.code = '2024/2025'
                RETURNING id
                """)
                .param(
                        "filename",
                        TEST_FILENAME
                )
                .query(Long.class)
                .single();
    }

    private void createInvalidRow(
            Long batchId
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
                    validation_error,
                    raw_data
                )
                VALUES (
                    :batchId,
                    2,
                    'ADMIN-TEST-002',
                    'Giocatore Non Valido',
                    'giocatore non valido',
                    'Napoli',
                    'napoli',
                    NULL,
                    FALSE,
                    'Ruolo non valido: X',
                    CAST(
                        '{"role":"X"}'
                        AS JSONB
                    )
                )
                """)
                .param(
                        "batchId",
                        batchId
                )
                .update();
    }
}