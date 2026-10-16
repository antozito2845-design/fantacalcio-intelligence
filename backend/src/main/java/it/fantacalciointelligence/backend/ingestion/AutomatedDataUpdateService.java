package it.fantacalciointelligence.backend.ingestion;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import it.fantacalciointelligence.backend.common.ConflictException;
import it.fantacalciointelligence.backend.refresh.DataRefreshResponse;
import it.fantacalciointelligence.backend.refresh.DataRefreshService;

@Service
public class AutomatedDataUpdateService {

    private final FantacalcioListoneProperties listoneProperties;
    private final FantacalcioListoneSyncService listoneSyncService;
    private final SourceIngestionProperties sourceIngestionProperties;
    private final SourceIngestionService sourceIngestionService;
    private final DataRefreshService refreshService;
    private final AutomationRunService automationRunService;

    @Autowired
    public AutomatedDataUpdateService(
            FantacalcioListoneProperties listoneProperties,
            FantacalcioListoneSyncService listoneSyncService,
            SourceIngestionProperties sourceIngestionProperties,
            SourceIngestionService sourceIngestionService,
            DataRefreshService refreshService,
            AutomationRunService automationRunService
    ) {
        this.listoneProperties = listoneProperties;
        this.listoneSyncService = listoneSyncService;
        this.sourceIngestionProperties = sourceIngestionProperties;
        this.sourceIngestionService = sourceIngestionService;
        this.refreshService = refreshService;
        this.automationRunService = automationRunService;
    }

    /*
     * Compatibility constructor used by existing focused unit tests.
     *
     * It intentionally preserves the historical isolated service-test path.
     * Spring production wiring always uses the @Autowired constructor above,
     * therefore real scheduled executions always use AutomationRunService.
     */
    AutomatedDataUpdateService(
            SourceIngestionProperties sourceIngestionProperties,
            SourceIngestionService sourceIngestionService,
            DataRefreshService refreshService
    ) {

        FantacalcioListoneProperties disabledListone =
                new FantacalcioListoneProperties();

        disabledListone.setEnabled(false);

        this.listoneProperties = disabledListone;
        this.listoneSyncService = null;
        this.sourceIngestionProperties = sourceIngestionProperties;
        this.sourceIngestionService = sourceIngestionService;
        this.refreshService = refreshService;
        this.automationRunService = null;
    }

    public AutomatedUpdateResponse run(
            String season
    ) {

        if (automationRunService == null) {
            return runPipeline(
                    season,
                    null
            );
        }

        long automationRunId =
                automationRunService.begin(
                        season
                );

        try {

            AutomatedUpdateResponse result =
                    runPipeline(
                            season,
                            automationRunId
                    );

            automationRunService.complete(
                    automationRunId,
                    result.listone() == null
                            ? null
                            : result.listone().runId(),
                    result.ingestion().size(),
                    result.refresh() == null
                            ? null
                            : result.refresh().id()
            );

            return result;

        } catch (RuntimeException exception) {

            automationRunService.fail(
                    automationRunId,
                    exception
            );

            throw exception;
        }
    }

    private AutomatedUpdateResponse runPipeline(
            String season,
            Long automationRunId
    ) {

        FantacalcioListoneSyncResponse listone =
                null;

        if (listoneProperties.isEnabled()) {

            heartbeat(
                    automationRunId,
                    "LISTONE"
            );

            if (listoneSyncService == null) {
                throw new IllegalStateException(
                        "Fantacalcio listone sync abilitato senza servizio configurato"
                );
            }

            listone =
                    listoneSyncService.sync(
                            season
                    );
        }

        List<SourceIngestionResponse> ingestion =
                List.of();

        if (sourceIngestionProperties.isEnabled()) {

            heartbeat(
                    automationRunId,
                    "INGESTION"
            );

            ingestion =
                    sourceIngestionService.ingestAll(
                            season
                    );

            boolean requiredFailed =
                    ingestion.stream()
                            .anyMatch(response ->
                                    response.required()
                                            && "FAILED".equals(
                                                    response.status()
                                            )
                            );

            if (requiredFailed) {
                throw new ConflictException(
                        "Ingestion richiesta fallita; refresh non eseguito"
                );
            }
        }

        heartbeat(
                automationRunId,
                "REFRESH"
        );

        DataRefreshResponse refresh =
                refreshService.runScheduled(
                        season
                );

        return new AutomatedUpdateResponse(
                listone,
                ingestion,
                refresh
        );
    }

    private void heartbeat(
            Long automationRunId,
            String stage
    ) {

        if (
                automationRunId != null
                        && automationRunService != null
        ) {
            automationRunService.stage(
                    automationRunId,
                    stage
            );
        }
    }

    public record AutomatedUpdateResponse(
            FantacalcioListoneSyncResponse listone,
            List<SourceIngestionResponse> ingestion,
            DataRefreshResponse refresh
    ) {

        public AutomatedUpdateResponse(
                List<SourceIngestionResponse> ingestion,
                DataRefreshResponse refresh
        ) {
            this(
                    null,
                    ingestion,
                    refresh
            );
        }
    }
}