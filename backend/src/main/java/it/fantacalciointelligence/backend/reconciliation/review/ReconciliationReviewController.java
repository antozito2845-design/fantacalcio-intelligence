package it.fantacalciointelligence.backend.reconciliation.review;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reconciliation/review")
public class ReconciliationReviewController {

    private final ReconciliationReviewService service;

    public ReconciliationReviewController(
            ReconciliationReviewService service
    ) {
        this.service = service;
    }

    @PostMapping("/sync")
    public ReconciliationReviewSyncResponse sync(
            @RequestParam long fantacalcioBatchId,
            @RequestParam long gazzettaBatchId,
            @RequestParam(required = false)
            Long fantamarktBatchId
    ) {
        return service.sync(
                fantacalcioBatchId,
                gazzettaBatchId,
                fantamarktBatchId
        );
    }

    @GetMapping
    public List<ReconciliationReviewItemResponse> findAll(
            @RequestParam String season,
            @RequestParam(required = false)
            String status
    ) {
        return service.findAll(
                season,
                status
        );
    }

    @PostMapping("/{reviewId}/resolve-link")
    public ReconciliationReviewItemResponse resolveLink(
            @PathVariable Long reviewId,
            @RequestBody
            ReconciliationReviewResolveRequest request
    ) {
        return service.resolveLink(
                reviewId,
                request
        );
    }

    @PostMapping("/{reviewId}/exclude")
    public ReconciliationReviewItemResponse exclude(
            @PathVariable Long reviewId,
            @RequestBody
            ReconciliationReviewExcludeRequest request
    ) {
        return service.exclude(
                reviewId,
                request
        );
    }
    @PostMapping("/{reviewId}/ignore")
    public ReconciliationReviewItemResponse ignore(
            @PathVariable Long reviewId,
            @RequestParam String note
    ) {
        return service.ignore(
                reviewId,
                note
        );
    }
}
