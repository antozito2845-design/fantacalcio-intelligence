package it.fantacalciointelligence.backend.ingestion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/automation")
public class AutomationRunController {

    private final AutomationRunService service;

    public AutomationRunController(
            AutomationRunService service
    ) {
        this.service = service;
    }

    @GetMapping("/latest")
    public AutomationRunResponse latest(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.latest(season);
    }
}