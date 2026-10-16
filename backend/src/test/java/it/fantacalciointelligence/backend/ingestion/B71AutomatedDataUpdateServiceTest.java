package it.fantacalciointelligence.backend.ingestion;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import it.fantacalciointelligence.backend.refresh.DataRefreshResponse;
import it.fantacalciointelligence.backend.refresh.DataRefreshService;

class B71AutomatedDataUpdateServiceTest {

    @Test
    void productionWiringWrapsRefreshInsideGlobalAutomationRun() {

        FantacalcioListoneProperties listone =
                new FantacalcioListoneProperties();

        listone.setEnabled(false);

        FantacalcioListoneSyncService listoneService =
                mock(
                        FantacalcioListoneSyncService.class
                );

        SourceIngestionProperties ingestionProperties =
                new SourceIngestionProperties();

        ingestionProperties.setEnabled(false);

        SourceIngestionService ingestion =
                mock(
                        SourceIngestionService.class
                );

        DataRefreshService refresh =
                mock(
                        DataRefreshService.class
                );

        AutomationRunService automation =
                mock(
                        AutomationRunService.class
                );

        DataRefreshResponse refreshResponse =
                new DataRefreshResponse(
                        99L,
                        "2026/2027",
                        "SCHEDULED",
                        "COMPLETED",
                        "FRESH",
                        null,
                        0,
                        0,
                        0,
                        null,
                        null,
                        null
                );

        when(
                automation.begin(
                        "2026/2027"
                )
        )
                .thenReturn(44L);

        when(
                refresh.runScheduled(
                        "2026/2027"
                )
        )
                .thenReturn(
                        refreshResponse
                );

        AutomatedDataUpdateService service =
                new AutomatedDataUpdateService(
                        listone,
                        listoneService,
                        ingestionProperties,
                        ingestion,
                        refresh,
                        automation
                );

        service.run(
                "2026/2027"
        );

        InOrder order =
                inOrder(
                        automation,
                        refresh
                );

        order.verify(
                automation
        )
                .begin(
                        "2026/2027"
                );

        order.verify(
                automation
        )
                .stage(
                        44L,
                        "REFRESH"
                );

        order.verify(
                refresh
        )
                .runScheduled(
                        "2026/2027"
                );

        order.verify(
                automation
        )
                .complete(
                        44L,
                        null,
                        0,
                        99L
                );

        verify(
                automation
        )
                .complete(
                        44L,
                        null,
                        0,
                        99L
                );
    }

    @Test
    void pipelineFailureMarksGlobalAutomationFailed() {

        FantacalcioListoneProperties listone =
                new FantacalcioListoneProperties();

        listone.setEnabled(false);

        SourceIngestionProperties ingestionProperties =
                new SourceIngestionProperties();

        ingestionProperties.setEnabled(false);

        FantacalcioListoneSyncService listoneService =
                mock(
                        FantacalcioListoneSyncService.class
                );

        SourceIngestionService ingestion =
                mock(
                        SourceIngestionService.class
                );

        DataRefreshService refresh =
                mock(
                        DataRefreshService.class
                );

        AutomationRunService automation =
                mock(
                        AutomationRunService.class
                );

        RuntimeException failure =
                new RuntimeException(
                        "refresh failure"
                );

        when(
                automation.begin(
                        "2026/2027"
                )
        )
                .thenReturn(45L);

        when(
                refresh.runScheduled(
                        "2026/2027"
                )
        )
                .thenThrow(
                        failure
                );

        AutomatedDataUpdateService service =
                new AutomatedDataUpdateService(
                        listone,
                        listoneService,
                        ingestionProperties,
                        ingestion,
                        refresh,
                        automation
                );

        try {
            service.run(
                    "2026/2027"
            );
        } catch (RuntimeException ignored) {
        }

        verify(
                automation
        )
                .fail(
                        45L,
                        failure
                );
    }
}