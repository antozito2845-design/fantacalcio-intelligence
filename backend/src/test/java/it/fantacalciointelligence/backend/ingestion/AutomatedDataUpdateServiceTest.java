package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.common.BadRequestException;
import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.refresh.DataRefreshService;

class AutomatedDataUpdateServiceTest {

    @Test
    void disabledPreservesRefreshOnly() {
        SourceIngestionProperties properties = new SourceIngestionProperties();
        SourceIngestionService ingestion = mock(SourceIngestionService.class);
        DataRefreshService refresh = mock(DataRefreshService.class);

        new AutomatedDataUpdateService(properties, ingestion, refresh).run("2026/2027");

        verify(refresh).runScheduled("2026/2027");
        verifyNoInteractions(ingestion);
    }

    @Test
    void requiredFailurePreventsRefresh() {
        SourceIngestionProperties properties = enabledProperties();
        SourceIngestionService ingestion = mock(SourceIngestionService.class);
        DataRefreshService refresh = mock(DataRefreshService.class);
        when(ingestion.ingestAll("s")).thenReturn(List.of(result("FAILED", true)));

        assertThatThrownBy(() -> new AutomatedDataUpdateService(
                properties, ingestion, refresh
        ).run("s")).isInstanceOf(ConflictException.class);

        verifyNoInteractions(refresh);
    }

    @Test
    void optionalFailureAndUnchangedRequiredAllowRefresh() {
        SourceIngestionProperties properties = enabledProperties();
        SourceIngestionService ingestion = mock(SourceIngestionService.class);
        DataRefreshService refresh = mock(DataRefreshService.class);
        when(ingestion.ingestAll("s")).thenReturn(List.of(
                result("FAILED", false),
                result("SKIPPED_UNCHANGED", true)
        ));

        new AutomatedDataUpdateService(properties, ingestion, refresh).run("s");

        verify(refresh).runScheduled("s");
    }

    @Test
    void zeroFeedsCannotSilentlyRefresh() {
        SourceIngestionProperties properties = enabledProperties();
        SourceIngestionService ingestion = mock(SourceIngestionService.class);
        DataRefreshService refresh = mock(DataRefreshService.class);
        when(ingestion.ingestAll("s")).thenThrow(new BadRequestException("Nessun feed"));

        assertThatThrownBy(() -> new AutomatedDataUpdateService(
                properties, ingestion, refresh
        ).run("s")).isInstanceOf(BadRequestException.class);

        verifyNoInteractions(refresh);
    }

    private SourceIngestionProperties enabledProperties() {
        SourceIngestionProperties properties = new SourceIngestionProperties();
        properties.setEnabled(true);
        return properties;
    }

    private SourceIngestionResponse result(String status, boolean required) {
        return new SourceIngestionResponse(
                1L, "f", "PLAYERS", "FANTACALCIO", "s", status,
                null, null, null, null, null, null, null, required
        );
    }
}
