package it.fantacalciointelligence.backend.freshness;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/data-freshness")
public class DataFreshnessController {

    private final DataFreshnessService service;

    public DataFreshnessController(
            DataFreshnessService service
    ) {
        this.service = service;
    }

    @GetMapping
    public DataFreshnessResponse evaluate(
            @RequestParam(
                    defaultValue = "#{@currentSeasonResolver.resolve()}"
            )
            String season
    ) {
        return service.evaluate(season);
    }
}
