package it.fantacalciointelligence.backend.liveauction;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auction/live")
public class LiveAuctionController {
    private final LiveAuctionDecisionService service;

    public LiveAuctionController(LiveAuctionDecisionService service) {
        this.service = service;
    }

    @PostMapping("/decision")
    public ResponseEntity<LiveAuctionDecisionResponse> decide(
            @Valid @RequestBody LiveAuctionDecisionRequest request) {
        return ResponseEntity.ok(service.decide(request));
    }
}
