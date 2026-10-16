package it.fantacalciointelligence.backend.importer.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/imports")
public class ImportBatchAdminController {

    private final ImportBatchAdminService service;

    public ImportBatchAdminController(
            ImportBatchAdminService service
    ) {
        this.service = service;
    }

    @GetMapping
    public ImportBatchPageResponse findBatches(
            @RequestParam(required = false)
            String source,

            @RequestParam(required = false)
            String season,

            @RequestParam(required = false)
            String status,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        return service.findBatches(
                source,
                season,
                status,
                page,
                size
        );
    }

    @GetMapping("/{batchId}")
    public ImportBatchDetailResponse findBatch(
            @PathVariable Long batchId,

            @RequestParam(defaultValue = "0")
            int errorPage,

            @RequestParam(defaultValue = "50")
            int errorSize
    ) {
        return service.findBatch(
                batchId,
                errorPage,
                errorSize
        );
    }
}