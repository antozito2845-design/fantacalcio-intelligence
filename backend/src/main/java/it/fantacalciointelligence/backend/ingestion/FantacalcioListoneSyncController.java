package it.fantacalciointelligence.backend.ingestion;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/fantacalcio-listone")
public class FantacalcioListoneSyncController {

    private final FantacalcioListoneSyncService service;

    public FantacalcioListoneSyncController(
            FantacalcioListoneSyncService service
    ) {
        this.service = service;
    }

    @PostMapping("/sync")
    public FantacalcioListoneSyncResponse sync(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.sync(season);
    }
}