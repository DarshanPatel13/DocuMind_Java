package com.darshan.documind.kafka;

import com.darshan.documind.dto.DocumentUploadedEvent;
import com.darshan.documind.service.IngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Entry point of the async ingestion pipeline. The consumer group
 * ({@code documind-ingestion}) and JSON deserialization are configured in
 * application.yml.
 *
 * <p>Any exception thrown here reaches the {@code DefaultErrorHandler} built
 * in KafkaConfig: 3 retries with exponential backoff, then mark the document
 * FAILED and park the event on document-events.DLT.</p>
 */
@Component
public class DocumentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventConsumer.class);

    private final IngestionService ingestionService;

    public DocumentEventConsumer(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = "${documind.kafka.document-events-topic}")
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        log.info("stage=consume documentId={} filename={}", event.documentId(), event.filename());
        ingestionService.ingest(event);
    }
}
