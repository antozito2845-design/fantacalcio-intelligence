package it.fantacalciointelligence.backend.scouting;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class B70PendingScoutingArrivalController {

    private final B70PendingScoutingArrivalService service;

    public B70PendingScoutingArrivalController(
            B70PendingScoutingArrivalService service
    ) {
        this.service = service;
    }

    @GetMapping("/api/v1/scouting/arrivals/pending")
    public List<B70PendingScoutingArrivalResponse> findPending(
            @RequestParam String season,
            @RequestParam(required = false)
            String role,
            @RequestParam(required = false)
            String club
    ) {

        return service.findPending(
                season,
                role,
                club
        );
    }
}