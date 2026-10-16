package it.fantacalciointelligence.backend.catalog.readiness;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/catalog")
public class CatalogReadinessController {

    private final CatalogReadinessService service;

    public CatalogReadinessController(
            CatalogReadinessService service
    ) {
        this.service = service;
    }

    @GetMapping("/readiness")
    public CatalogReadinessResponse readiness(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.evaluateLatest(season);
    }
}
