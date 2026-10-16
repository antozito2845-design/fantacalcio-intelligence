package it.fantacalciointelligence.backend.freshness;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class DataFreshnessServiceTest {

    private DataFreshnessService service;

    @Autowired
    private JdbcClient jdbcClient;

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-08-13T12:00:00Z");

    @BeforeEach
    void useFixedUtcClock() {
        service = new DataFreshnessService(
                jdbcClient,
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldReportFreshSources() {
        prepareFreshProductionImports();

        DataFreshnessResponse response =
                service.evaluate(
                        "2026/2027"
                );

        assertThat(
                response.overallStatus()
        ).isEqualTo("READY");

        assertThat(
                response.sources()
        )
                .extracting(
                        SourceFreshnessResponse::status
                )
                .containsOnly("FRESH");
    }

    @Test
    void shouldReportWarningForAgingRequiredSource() {
        prepareFreshProductionImports();

        ageAllSourceBatches(
                "GAZZETTA",
                100
        );

        DataFreshnessResponse response =
                service.evaluate(
                        "2026/2027"
                );

        assertThat(
                response.overallStatus()
        ).isEqualTo("WARNING");

        assertThat(
                statusFor(
                        response,
                        "GAZZETTA"
                )
        ).isEqualTo("AGING");

        assertThat(response.blockers())
                .isEmpty();

        assertThat(response.warnings())
                .contains("GAZZETTA è AGING: snapshot di 100 ore");
    }

    @Test
    void shouldBlockStaleRequiredSource() {
        prepareFreshProductionImports();

        ageAllSourceBatches(
                "FANTACALCIO",
                200
        );

        DataFreshnessResponse response =
                service.evaluate(
                        "2026/2027"
                );

        assertThat(
                response.overallStatus()
        ).isEqualTo("BLOCKED");

        assertThat(
                statusFor(
                        response,
                        "FANTACALCIO"
                )
        ).isEqualTo("STALE");

        assertThat(response.blockers())
                .contains("FANTACALCIO è STALE: snapshot di 200 ore");
    }

    @Test
    void shouldTruncateOnlyARealSubHourAtTheBoundary() {
        prepareFreshProductionImports();

        setAllSourceBatchesObservedAt(
                "GAZZETTA",
                OffsetDateTime.ofInstant(
                        FIXED_INSTANT.minusSeconds(
                                100L * 60 * 60 - 1
                        ),
                        ZoneOffset.UTC
                )
        );

        DataFreshnessResponse response =
                service.evaluate("2026/2027");

        SourceFreshnessResponse gazzetta =
                response.sources().stream()
                        .filter(source ->
                                "GAZZETTA".equals(source.source())
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(gazzetta.ageHours()).isEqualTo(99);
        assertThat(gazzetta.status()).isEqualTo("AGING");
    }

    @Test
    void shouldOnlyWarnForStaleOptionalFantamarkt() {
        prepareFreshProductionImports();

        ageAllSourceBatches(
                "FANTAMARKT",
                500
        );

        DataFreshnessResponse response =
                service.evaluate(
                        "2026/2027"
                );

        assertThat(
                response.overallStatus()
        ).isEqualTo("WARNING");

        assertThat(
                statusFor(
                        response,
                        "FANTAMARKT"
                )
        ).isEqualTo("STALE");

        assertThat(response.blockers())
                .isEmpty();
    }

    private String statusFor(
            DataFreshnessResponse response,
            String source
    ) {

        return response.sources()
                .stream()
                .filter(item ->
                        source.equals(
                                item.source()
                        )
                )
                .findFirst()
                .orElseThrow()
                .status();
    }

    private void prepareFreshProductionImports() {
        createBatch(
                "FANTACALCIO",
                "freshness-fantacalcio.csv"
        );

        createBatch(
                "GAZZETTA",
                "freshness-gazzetta.csv"
        );

        createBatch(
                "FANTAMARKT",
                "freshness-fantamarkt.csv"
        );
    }

    private void createBatch(
            String source,
            String filename
    ) {

        jdbcClient.sql("""
                INSERT INTO import_batches (
                    source,
                    season_id,
                    original_filename,
                    status,
                    total_rows,
                    valid_rows,
                    invalid_rows,
                    completed_at,
                    source_observed_at
                )

                SELECT
                    :source,
                    season.id,
                    :filename,
                    'COMPLETED',
                    1,
                    1,
                    0,
                    :now,
                    :now

                FROM seasons season
                WHERE season.code = '2026/2027'
                """)
                .param("source", source)
                .param("filename", filename)
                .param(
                        "now",
                        OffsetDateTime.ofInstant(
                                FIXED_INSTANT,
                                ZoneOffset.UTC
                        )
                )
                .update();
    }

    private void ageAllSourceBatches(
            String source,
            int hours
    ) {

        setAllSourceBatchesObservedAt(
                source,
                OffsetDateTime.ofInstant(
                        FIXED_INSTANT.minusSeconds(
                                hours * 60L * 60
                        ),
                        ZoneOffset.UTC
                )
        );
    }

    private void setAllSourceBatchesObservedAt(
            String source,
            OffsetDateTime observedAt
    ) {

        jdbcClient.sql("""
                UPDATE import_batches batch
                SET
                    source_observed_at = :observedAt,
                    completed_at = :observedAt

                FROM seasons season

                WHERE season.id =
                        batch.season_id

                  AND season.code =
                        '2026/2027'

                  AND batch.source =
                        :source

                  AND batch.status =
                        'COMPLETED'

                  AND LOWER(batch.original_filename)
                        NOT LIKE '%test%'
                """)
                .param("observedAt", observedAt)
                .param("source", source)
                .update();
    }
}
