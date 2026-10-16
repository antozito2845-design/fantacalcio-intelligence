package it.fantacalciointelligence.backend.availability;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/availabilities")
public class PlayerAvailabilityController {

    private final PlayerAvailabilityService service;

    public PlayerAvailabilityController(
            PlayerAvailabilityService service
    ) {
        this.service = service;
    }

    @GetMapping
    public List<PlayerAvailabilityResponse> findByMatchday(

            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}")
            String season,

            @RequestParam
            int matchday,

            @RequestParam(required = false)
            String status
    ) {
        return service.findByMatchday(
                season,
                matchday,
                status
        );
    }

    @GetMapping("/players/{playerId}/history")
    public List<PlayerAvailabilityHistoryResponse> findHistory(

            @PathVariable
            Long playerId,

            @RequestParam(defaultValue = "#{@currentSeasonResolver.resolve()}")
            String season,

            @RequestParam(required = false)
            Integer matchday
    ) {
        return service.findHistory(
                playerId,
                season,
                matchday
        );
    }

    @PutMapping("/players/{playerId}")
    public PlayerAvailabilityResponse update(

            @PathVariable
            Long playerId,

            @Valid
            @RequestBody
            UpdatePlayerAvailabilityRequest request
    ) {
        return service.update(
                playerId,
                request
        );
    }
}
