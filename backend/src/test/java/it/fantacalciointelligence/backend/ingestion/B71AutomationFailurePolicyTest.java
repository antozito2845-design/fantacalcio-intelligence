package it.fantacalciointelligence.backend.ingestion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.refresh.DataRefreshService;

class B71AutomationFailurePolicyTest {

    @Test
    void listoneFailureMarksGlobalAutomationFailedAndStopsPipeline() {

        FantacalcioListoneProperties listoneProperties =
                new FantacalcioListoneProperties();

        listoneProperties.setEnabled(true);

        FantacalcioListoneSyncService listone =
                mock(FantacalcioListoneSyncService.class);

        SourceIngestionProperties ingestionProperties =
                new SourceIngestionProperties();

        ingestionProperties.setEnabled(false);

        SourceIngestionService ingestion =
                mock(SourceIngestionService.class);

        DataRefreshService refresh =
                mock(DataRefreshService.class);

        AutomationRunService automation =
                mock(AutomationRunService.class);

        RuntimeException failure =
                new RuntimeException("listone failure");

        when(
                automation.begin("2026/2027")
        )
                .thenReturn(71L);

        when(
                listone.sync("2026/2027")
        )
                .thenThrow(failure);

        AutomatedDataUpdateService service =
                new AutomatedDataUpdateService(
                        listoneProperties,
                        listone,
                        ingestionProperties,
                        ingestion,
                        refresh,
                        automation
                );

        assertThatThrownBy(() ->
                service.run("2026/2027")
        )
                .isSameAs(failure);

        verify(automation)
                .stage(
                        71L,
                        "LISTONE"
                );

        verify(automation)
                .fail(
                        71L,
                        failure
                );

        verifyNoInteractions(ingestion);
        verifyNoInteractions(refresh);

        verify(
                automation,
                never()
        )
                .complete(
                        71L,
                        null,
                        0,
                        null
                );
    }

    @Test
    void requiredIngestionFailureMarksAutomationFailedAndPreventsRefresh() {

        FantacalcioListoneProperties listoneProperties =
                new FantacalcioListoneProperties();

        listoneProperties.setEnabled(false);

        FantacalcioListoneSyncService listone =
                mock(FantacalcioListoneSyncService.class);

        SourceIngestionProperties ingestionProperties =
                new SourceIngestionProperties();

        ingestionProperties.setEnabled(true);

        SourceIngestionService ingestion =
                mock(SourceIngestionService.class);

        DataRefreshService refresh =
                mock(DataRefreshService.class);

        AutomationRunService automation =
                mock(AutomationRunService.class);

        when(
                automation.begin("2026/2027")
        )
                .thenReturn(72L);

        when(
                ingestion.ingestAll("2026/2027")
        )
                .thenReturn(
                        List.of(
                                requiredFailure()
                        )
                );

        AutomatedDataUpdateService service =
                new AutomatedDataUpdateService(
                        listoneProperties,
                        listone,
                        ingestionProperties,
                        ingestion,
                        refresh,
                        automation
                );

        assertThatThrownBy(() ->
                service.run("2026/2027")
        )
                .isInstanceOf(
                        ConflictException.class
                )
                .hasMessageContaining(
                        "Ingestion richiesta fallita"
                );

        verify(automation)
                .stage(
                        72L,
                        "INGESTION"
                );

        verifyNoInteractions(refresh);

        verify(
                automation,
                never()
        )
                .complete(
                        72L,
                        null,
                        0,
                        null
                );

        verify(automation)
                .fail(
                        org.mockito.ArgumentMatchers.eq(72L),
                        org.mockito.ArgumentMatchers.any(
                                ConflictException.class
                        )
                );
    }

    @Test
    void refreshFailureMarksAutomationFailed() {

        FantacalcioListoneProperties listoneProperties =
                new FantacalcioListoneProperties();

        listoneProperties.setEnabled(false);

        FantacalcioListoneSyncService listone =
                mock(FantacalcioListoneSyncService.class);

        SourceIngestionProperties ingestionProperties =
                new SourceIngestionProperties();

        ingestionProperties.setEnabled(false);

        SourceIngestionService ingestion =
                mock(SourceIngestionService.class);

        DataRefreshService refresh =
                mock(DataRefreshService.class);

        AutomationRunService automation =
                mock(AutomationRunService.class);

        RuntimeException failure =
                new RuntimeException(
                        "refresh failure"
                );

        when(
                automation.begin("2026/2027")
        )
                .thenReturn(73L);

        when(
                refresh.runScheduled("2026/2027")
        )
                .thenThrow(failure);

        AutomatedDataUpdateService service =
                new AutomatedDataUpdateService(
                        listoneProperties,
                        listone,
                        ingestionProperties,
                        ingestion,
                        refresh,
                        automation
                );

        assertThatThrownBy(() ->
                service.run("2026/2027")
        )
                .isSameAs(failure);

        verify(automation)
                .stage(
                        73L,
                        "REFRESH"
                );

        verify(automation)
                .fail(
                        73L,
                        failure
                );

        verify(
                automation,
                never()
        )
                .complete(
                        73L,
                        null,
                        0,
                        null
                );
    }

    private SourceIngestionResponse requiredFailure() {

        return new SourceIngestionResponse(
                1L,
                "required-feed",
                "PLAYERS",
                "FANTACALCIO",
                "2026/2027",
                "FAILED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                true
        );
    }
}