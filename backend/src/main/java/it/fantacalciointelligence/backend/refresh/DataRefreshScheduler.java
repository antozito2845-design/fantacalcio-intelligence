package it.fantacalciointelligence.backend.refresh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.ingestion.AutomatedDataUpdateService;
import it.fantacalciointelligence.backend.season.CurrentSeasonResolver;

@Component
@ConditionalOnProperty(
        name = "app.data-refresh.scheduler.enabled",
        havingValue = "true"
)
public class DataRefreshScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DataRefreshScheduler.class);

    private final AutomatedDataUpdateService service;
    private final String configuredSeason;
    private final CurrentSeasonResolver currentSeasonResolver;

    public DataRefreshScheduler(
            AutomatedDataUpdateService service,
            CurrentSeasonResolver currentSeasonResolver,
            @Value("${app.data-refresh.scheduler.season:}")
            String configuredSeason
    ) {
        this.service = service;
        this.currentSeasonResolver = currentSeasonResolver;
        this.configuredSeason = configuredSeason;
    }

    @Scheduled(
            cron = "${app.data-refresh.scheduler.cron:0 0 0 * * *}",
            zone = "Europe/Rome"
    )
    public void refreshData() {
        try {
            String season = configuredSeason == null
                    || configuredSeason.isBlank()
                    ? currentSeasonResolver.resolve()
                    : configuredSeason.trim();

            AutomatedDataUpdateService.AutomatedUpdateResponse result =
                    service.run(season);

            DataRefreshResponse response = result.refresh();

            LOGGER.info(
                    "Aggiornamento automatico completato: "
                            + "runId={}, stagione={}, giocatori={}, "
                            + "valutazioni={}, rating={}",
                    response.id(),
                    response.season(),
                    response.reconciliationMatchedPlayers(),
                    response.generatedEvaluations(),
                    response.generatedClubRatings()
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Aggiornamento automatico fallito",
                    exception
            );
        }
    }
}