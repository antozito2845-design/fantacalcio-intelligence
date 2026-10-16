package it.fantacalciointelligence.backend.transfer.detection;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/transfers/detection")
public class TransferDetectionController {

    private final TransferDetectionService service;

    public TransferDetectionController(
            TransferDetectionService service
    ) {
        this.service = service;
    }

    @PostMapping("/run")
    public TransferDetectionResponse detect(
            @RequestParam long fantacalcioBatchId,
            @RequestParam(required = false)
            Long gazzettaBatchId,
            @RequestParam(required = false)
            Long fantamarktBatchId
    ) {
        return service.detect(
                fantacalcioBatchId,
                gazzettaBatchId,
                fantamarktBatchId
        );
    }
}