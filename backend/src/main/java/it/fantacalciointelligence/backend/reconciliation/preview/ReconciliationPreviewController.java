package it.fantacalciointelligence.backend.reconciliation.preview;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/reconciliation")
public class ReconciliationPreviewController {

    private final ReconciliationPreviewService service;

    public ReconciliationPreviewController(
            ReconciliationPreviewService service
    ) {
        this.service = service;
    }

    @GetMapping("/preview")
    public ReconciliationPreviewResponse preview(
            @RequestParam
            long fantacalcioBatchId,

            @RequestParam
            long gazzettaBatchId,

            @RequestParam(required = false)
            Long fantamarktBatchId,

            @RequestParam(defaultValue = "true")
            boolean onlyUnresolved
    ) {

        return service.preview(
                fantacalcioBatchId,
                gazzettaBatchId,
                fantamarktBatchId,
                onlyUnresolved
        );
    }
}