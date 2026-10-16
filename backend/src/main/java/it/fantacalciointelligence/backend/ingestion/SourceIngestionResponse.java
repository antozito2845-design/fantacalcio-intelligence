package it.fantacalciointelligence.backend.ingestion;

import java.time.OffsetDateTime;

public record SourceIngestionResponse(Long id,String feedId,String feedType,String source,String season,
        String status,Integer httpStatus,Long payloadBytes,String checksum,Long importBatchId,
        String errorMessage,OffsetDateTime startedAt,OffsetDateTime completedAt,boolean required) {}
