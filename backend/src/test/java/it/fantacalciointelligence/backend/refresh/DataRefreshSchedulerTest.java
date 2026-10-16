package it.fantacalciointelligence.backend.refresh;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import it.fantacalciointelligence.backend.ingestion.AutomatedDataUpdateService;
import it.fantacalciointelligence.backend.ingestion.AutomatedDataUpdateService.AutomatedUpdateResponse;
import it.fantacalciointelligence.backend.season.CurrentSeasonResolver;

class DataRefreshSchedulerTest {
    @Test
    void missingOverrideUsesCurrentSeason() {
        AutomatedDataUpdateService service = mock(AutomatedDataUpdateService.class);
        CurrentSeasonResolver resolver = mock(CurrentSeasonResolver.class);
        when(resolver.resolve()).thenReturn("2027/2028");
        when(service.run("2027/2028")).thenReturn(new AutomatedUpdateResponse(List.of(), response("2027/2028")));
        new DataRefreshScheduler(service, resolver, " ").refreshData();
        verify(service).run("2027/2028");
    }

    @Test
    void configuredOverrideWins() {
        AutomatedDataUpdateService service = mock(AutomatedDataUpdateService.class);
        CurrentSeasonResolver resolver = mock(CurrentSeasonResolver.class);
        when(service.run("2025/2026")).thenReturn(new AutomatedUpdateResponse(List.of(), response("2025/2026")));
        new DataRefreshScheduler(service, resolver, " 2025/2026 ").refreshData();
        verify(service).run("2025/2026");
    }

    private DataRefreshResponse response(String season) {
        return new DataRefreshResponse(1L, season, "SCHEDULED", "COMPLETED", "FRESH", null,
                0, 0, 0, null, null, null);
    }
}
