package com.darshan.documind.config;

import com.darshan.documind.dto.DocumentUploadedEvent;
import com.darshan.documind.entity.DocumentStatus;
import com.darshan.documind.exception.DocumentNotFoundException;
import com.darshan.documind.repository.DocumentRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Topic provisioning and the consumer retry/recovery policy.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /** Created automatically at startup by Spring's KafkaAdmin. */
    @Bean
    public NewTopic documentEventsTopic(DocuMindProperties properties) {
        return TopicBuilder.name(properties.kafka().documentEventsTopic())
                .partitions(3)   // 3 partitions = room for 3 parallel consumers later
                .replicas(1)     // single-broker dev cluster
                .build();
    }

    /**
     * The DLT mirrors the source partition count because
     * DeadLetterPublishingRecoverer routes a failed record to the SAME
     * partition number on the DLT by default.
     */
    @Bean
    public NewTopic documentEventsDltTopic(DocuMindProperties properties) {
        return TopicBuilder.name(properties.kafka().documentEventsDltTopic())
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Retry/recovery policy for every @KafkaListener (Spring Boot wires a
     * unique CommonErrorHandler bean into the default listener factory).
     *
     * <p>Policy: 3 retries with exponential backoff (1s, 2s, 4s), then recover
     * by marking the document FAILED in Postgres and publishing the original
     * event to document-events.DLT for inspection or replay.</p>
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                 DocumentRepository documentRepository) {
        DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            // The listener exception arrives wrapped (ListenerExecutionFailedException).
            Throwable root = ex.getCause() != null ? ex.getCause() : ex;
            if (record.value() instanceof DocumentUploadedEvent event) {
                documentRepository.findById(event.documentId()).ifPresent(doc -> {
                    doc.setStatus(DocumentStatus.FAILED);
                    doc.setFailureReason(root.getMessage());
                    documentRepository.save(doc);
                });
                log.error("stage=dlt documentId={} reason={}", event.documentId(), root.getMessage());
            }
            dltRecoverer.accept(record, ex);
        };

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // A document row that does not exist will never appear by retrying.
        handler.addNotRetryableExceptions(DocumentNotFoundException.class);
        return handler;
    }
}
