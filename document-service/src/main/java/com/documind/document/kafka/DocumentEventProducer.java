package com.documind.document.kafka;

import com.documind.contracts.DocumentUploadedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes the ingestion event after an upload. Keyed by {@code documentId} so
 * all events for one document keep partition order (and the work can scale
 * horizontally up to the partition count).
 */
@Component
public class DocumentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public DocumentEventProducer(KafkaTemplate<String, Object> kafkaTemplate,
                                 @Value("${documind.kafka.document-events-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    public void publish(DocumentUploadedEvent event) {
        kafkaTemplate.send(topic, event.documentId().toString(), event);
    }
}
