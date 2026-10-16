package it.fantacalciointelligence.backend.auctionstate;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.CreateSessionRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.NominationRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PurchaseRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.PurchaseResponse;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.SessionDecisionRequest;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.SessionResponse;
import it.fantacalciointelligence.backend.auctionstate.AuctionStateDtos.StateResponse;
import it.fantacalciointelligence.backend.liveauction.LiveAuctionDecisionResponse;
import it.fantacalciointelligence.backend.squad.SquadService;

@RestController
@RequestMapping("/api/v1/auction/sessions")
public class AuctionStateController {

    private final AuctionStateService state;
    private final AuctionPurchaseService purchases;
    private final AuctionContextAdapter adapter;
    private final AuctionSessionAccessService access;
    private final SquadService squads;

    public AuctionStateController(
            AuctionStateService state,
            AuctionPurchaseService purchases,
            AuctionContextAdapter adapter,
            AuctionSessionAccessService access,
            SquadService squads
    ) {
        this.state = state;
        this.purchases = purchases;
        this.adapter = adapter;
        this.access = access;
        this.squads = squads;
    }

    @PostMapping
    public ResponseEntity<SessionResponse> create(
            @Valid @RequestBody CreateSessionRequest request,
            Authentication authentication
    ) {

        String owner =
                access.authenticatedUsername(authentication);

        squads.assertAccess(
                request.squadId(),
                owner,
                authentication.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))
        );

        return ResponseEntity.ok(
                state.create(
                        request,
                        owner
                )
        );
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<SessionResponse> activate(
            @PathVariable Long id,
            Authentication authentication
    ) {

        access.requireAccess(
                id,
                authentication
        );

        return ResponseEntity.ok(
                state.activate(id)
        );
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<SessionResponse> complete(
            @PathVariable Long id,
            Authentication authentication
    ) {

        access.requireAccess(
                id,
                authentication
        );

        return ResponseEntity.ok(
                state.complete(id)
        );
    }

    @PostMapping("/{id}/nomination")
    public ResponseEntity<SessionResponse> nominate(
            @PathVariable Long id,
            @Valid @RequestBody NominationRequest request,
            Authentication authentication
    ) {

        access.requireAccess(
                id,
                authentication
        );

        return ResponseEntity.ok(
                state.nominate(
                        id,
                        request
                )
        );
    }

    @PostMapping("/{id}/purchases")
    public ResponseEntity<PurchaseResponse> purchase(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseRequest request,
            Authentication authentication
    ) {

        access.requireAccess(
                id,
                authentication
        );

        return ResponseEntity.ok(
                purchases.purchase(
                        id,
                        request
                )
        );
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<LiveAuctionDecisionResponse> decide(
            @PathVariable Long id,
            @Valid @RequestBody SessionDecisionRequest request,
            Authentication authentication
    ) {

        access.requireAccess(
                id,
                authentication
        );

        return ResponseEntity.ok(
                adapter.decide(
                        id,
                        request
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> find(
            @PathVariable Long id,
            Authentication authentication
    ) {

        access.requireAccess(
                id,
                authentication
        );

        return ResponseEntity.ok(
                state.find(id)
        );
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<StateResponse> state(
            @PathVariable Long id,
            Authentication authentication
    ) {

        access.requireAccess(
                id,
                authentication
        );

        return ResponseEntity.ok(
                state.state(id)
        );
    }
}
