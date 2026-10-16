package it.fantacalciointelligence.backend.scouting;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class B70ArrivalScoutingController {

    private final B70ArrivalScoutingService service;

    public B70ArrivalScoutingController(
            B70ArrivalScoutingService service
    ) {
        this.service = service;
    }

    @GetMapping("/api/v1/scouting/arrivals")
    public List<B70ArrivalScoutingResponse> findCurrentArrivals(
            @RequestParam String season,
            @RequestParam(required = false)
            String recommendation,
            @RequestParam(required = false)
            String role,
            @RequestParam(required = false)
            String club
    ) {

        return service.findCurrentArrivals(
                season,
                recommendation,
                role,
                club
        );
    }
}