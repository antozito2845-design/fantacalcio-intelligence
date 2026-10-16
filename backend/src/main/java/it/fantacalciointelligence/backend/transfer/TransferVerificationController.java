package it.fantacalciointelligence.backend.transfer;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/transfers")
public class TransferVerificationController {

    private final TransferVerificationService service;

    public TransferVerificationController(
            TransferVerificationService service
    ) {
        this.service = service;
    }

    @PostMapping("/claims")
    public TransferClaimResponse createClaim(
            @Valid @RequestBody TransferClaimRequest request
    ) {
        return service.createClaim(request);
    }

    @PostMapping("/{claimId}/verify-official")
    public TransferClaimResponse verifyOfficial(
            @PathVariable Long claimId,
            @RequestBody OfficialTransferVerificationRequest request
    ) {
        return service.verifyOfficial(claimId, request);
    }

    @PostMapping("/{claimId}/reject")
    public TransferClaimResponse reject(
            @PathVariable Long claimId,
            @RequestParam String reason
    ) {
        return service.reject(
                claimId,
                reason
        );
    }

    @GetMapping("/pending")
    public List<TransferClaimResponse> findPending(
            @RequestParam String season
    ) {
        return service.findPending(season);
    }
}
