package it.fantacalciointelligence.backend.importer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;

import it.fantacalciointelligence.backend.common.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class PlayerImportServiceTest {

    private static final String TEST_FILE_PREFIX =
            "professional-import-test-";

    @Autowired
    private PlayerImportService service;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void cleanImportedBatches() {
        jdbcClient.sql("""
                DELETE FROM import_batches
                WHERE original_filename
                    LIKE :filenamePattern
                """)
                .param(
                        "filenamePattern",
                        TEST_FILE_PREFIX + "%"
                )
                .update();
    }

    @Test
    void shouldImportValidAndInvalidRows() {

        MockMultipartFile file =
                csvFile(
                        TEST_FILE_PREFIX
                                + "mixed.csv",
                        """
                        external_id,name,club,role,quotation,fmv
                        TEST-001,Giocatore Valido,Napoli,A,25,40
                        ,Senza Identificativo,Inter,C,10,20
                        TEST-003,Senza Squadra,,D,8,12
                        TEST-004,Ruolo Errato,Roma,X,5,10
                        TEST-005,Numero Errato,Milan,P,abc,-2
                        TEST-001,Duplicato Identificativo,Napoli,A,4,8
                        TEST-007,Giocatore Valido,Napoli,A,6,9
                        TEST-008,Giocatore Valido,Napoli,A,7,10
                        """
                );

        PlayerImportResponse response =
                service.importCsv(
                        "fantacalcio",
                        "2024/2025",
                        file
                );

        assertThat(response.status())
                .isEqualTo("COMPLETED");

        assertThat(response.totalRows())
                .isEqualTo(8);

        assertThat(response.validRows())
                .isEqualTo(1);

        assertThat(response.invalidRows())
                .isEqualTo(7);

        assertThat(response.errorMessage())
                .isNull();

        Map<String, Object> batch =
                findBatch(
                        response.batchId()
                );

        assertThat(batch.get("status"))
                .isEqualTo("COMPLETED");

        assertThat(
                ((Number) batch.get(
                        "total_rows"
                )).intValue()
        ).isEqualTo(8);

        List<Map<String, Object>> invalidRows =
                jdbcClient.sql("""
                        SELECT
                            row_number,
                            validation_error
                        FROM player_import_rows
                        WHERE batch_id = :batchId
                          AND valid = FALSE
                        ORDER BY row_number
                        """)
                        .param(
                                "batchId",
                                response.batchId()
                        )
                        .query()
                        .listOfRows();

        assertThat(invalidRows)
                .hasSize(7);

        assertThat(invalidRows)
                .extracting(row ->
                        String.valueOf(
                                row.get(
                                        "validation_error"
                                )
                        )
                )
                .anyMatch(error ->
                        error.contains(
                                "ID esterno mancante"
                        )
                )
                .anyMatch(error ->
                        error.contains(
                                "Squadra mancante"
                        )
                )
                .anyMatch(error ->
                        error.contains(
                                "Ruolo non valido"
                        )
                )
                .anyMatch(error ->
                        error.contains(
                                "Quotazione non valida"
                        )
                )
                .anyMatch(error ->
                        error.contains(
                                "FMV non può essere negativa"
                        )
                )
                .anyMatch(error ->
                        error.contains(
                                "ID esterno duplicato"
                        )
                )
                .anyMatch(error ->
                        error.contains(
                                "Giocatore duplicato"
                        )
                );
    }

    @Test
    void shouldPreserveFailedBatchWhenHeadersAreMissing() {

        String filename =
                TEST_FILE_PREFIX
                        + "missing-headers.csv";

        MockMultipartFile file =
                csvFile(
                        filename,
                        """
                        player,team
                        Mario Rossi,Napoli
                        """
                );

        assertThatThrownBy(() ->
                service.importCsv(
                        "GAZZETTA",
                        "2024/2025",
                        file
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "Colonne obbligatorie mancanti"
                );

        Map<String, Object> batch =
                findLatestBatchByFilename(
                        filename
                );

        assertThat(batch.get("status"))
                .isEqualTo("FAILED");

        assertThat(
                String.valueOf(
                        batch.get(
                                "error_message"
                        )
                )
        ).contains(
                "Colonne obbligatorie mancanti"
        );

        assertThat(
                ((Number) batch.get(
                        "total_rows"
                )).intValue()
        ).isZero();
    }

    @Test
    void shouldPreserveFailedBatchForEmptyCsv() {

        String filename =
                TEST_FILE_PREFIX
                        + "empty.csv";

        MockMultipartFile file =
                csvFile(
                        filename,
                        """
                        external_id,name,club,role,quotation,fmv
                        """
                );

        assertThatThrownBy(() ->
                service.importCsv(
                        "FANTAMARKT",
                        "2024/2025",
                        file
                )
        )
                .isInstanceOf(
                        BadRequestException.class
                )
                .hasMessageContaining(
                        "non contiene righe dati"
                );

        Map<String, Object> batch =
                findLatestBatchByFilename(
                        filename
                );

        assertThat(batch.get("status"))
                .isEqualTo("FAILED");

        assertThat(
                String.valueOf(
                        batch.get(
                                "error_message"
                        )
                )
        ).contains(
                "non contiene righe dati"
        );
    }

    @Test
    void shouldRejectInvalidSourceBeforeCreatingBatch() {

        String filename =
                TEST_FILE_PREFIX
                        + "invalid-source.csv";

        MockMultipartFile file =
                csvFile(
                        filename,
                        """
                        external_id,name,club,role
                        TEST-001,Giocatore Test,Napoli,A
                        """
                );

        assertThatThrownBy(() ->
                service.importCsv(
                        "SORGENTE_INESISTENTE",
                        "2024/2025",
                        file
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
                        FROM import_batches
                        WHERE original_filename =
                            :filename
                        """)
                        .param(
                                "filename",
                                filename
                        )
                        .query(Integer.class)
                        .single();

        assertThat(batches)
                .isZero();
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

    private Map<String, Object> findBatch(
            Long batchId
    ) {
        return jdbcClient.sql("""
                SELECT
                    id,
                    status,
                    total_rows,
                    valid_rows,
                    invalid_rows,
                    error_message
                FROM import_batches
                WHERE id = :batchId
                """)
                .param(
                        "batchId",
                        batchId
                )
                .query()
                .singleRow();
    }

    private Map<String, Object>
            findLatestBatchByFilename(
                    String filename
            ) {
        return jdbcClient.sql("""
                SELECT
                    id,
                    status,
                    total_rows,
                    valid_rows,
                    invalid_rows,
                    error_message
                FROM import_batches
                WHERE original_filename =
                    :filename
                ORDER BY id DESC
                LIMIT 1
                """)
                .param(
                        "filename",
                        filename
                )
                .query()
                .singleRow();
    }
}