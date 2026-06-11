package com.darshan.documind.kafka;

import com.darshan.documind.config.DocuMindProperties;
import com.darshan.documind.dto.DocumentUploadedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link DocumentUploadedEvent} to the document-events topic.
 *
 * <p>The documentId is the message KEY, so every event for one document lands
 * on the same partition and is processed in order — important if the same
 * document is ever re-uploaded or replayed.</p>
 */
@Component
public class DocumentEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DocumentEventProducer.class);

    // <Object, Object> matches Spring Boot's auto-configured KafkaTemplate bean.
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final DocuMindProperties properties;

    public DocumentEventProducer(KafkaTemplate<Object, Object> kafkaTemplate,
                                 DocuMindProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    public void publish(DocumentUploadedEvent event) {
        String topic = properties.kafka().documentEventsTopic();
        kafkaTemplate.send(topic, event.documentId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("stage=publish documentId={} topic={} status=failed",
                                event.documentId(), topic, ex);
                    } else {
                        log.info("stage=publish documentId={} topic={} partition={} offset={}",
                                event.documentId(), topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
