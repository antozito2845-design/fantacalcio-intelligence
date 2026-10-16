package it.fantacalciointelligence.backend.intelligence;

import org.springframework.stereotype.Component;

import it.fantacalciointelligence.backend.catalog.readiness.CatalogReadinessService;
import it.fantacalciointelligence.backend.common.ConflictException;

@Component
public class IntelligenceCatalogPolicy {
    private final CatalogReadinessService readinessService;

    public IntelligenceCatalogPolicy(CatalogReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    public void assertReadable(String season, boolean currentSeason) {
        if (currentSeason && "BLOCKED".equals(readinessService.evaluateLatest(season).status())) {
            throw new ConflictException("Catalog Quality Gate BLOCKED: profilo intelligence non generabile");
        }
    }
}
