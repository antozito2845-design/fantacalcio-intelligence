package it.fantacalciointelligence.backend.identity.enrichment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/admin/identity/readiness")
public class IdentityEnrichmentReadinessController {
    private final IdentityEnrichmentReadinessService service;

    public IdentityEnrichmentReadinessController(IdentityEnrichmentReadinessService service) {
        this.service = service;
    }

    @GetMapping
    public IdentityEnrichmentReadinessResponse evaluate(
            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}") String season) {
        return service.evaluate(season);
    }
}
