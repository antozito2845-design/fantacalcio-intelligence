package it.fantacalciointelligence.backend.refresh;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/data-refresh")
public class DataRefreshController {

    private final DataRefreshService service;

    public DataRefreshController(
            DataRefreshService service
    ) {
        this.service = service;
    }

    @PostMapping("/run")
    public DataRefreshResponse run(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.runManual(season);
    }

    @GetMapping("/latest")
    public DataRefreshResponse findLatest(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.findLatest(season);
    }
}
