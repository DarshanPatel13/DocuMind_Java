package com.documind.document.config;

import com.documind.contracts.DocumentStatus;
import com.documind.contracts.DocumentUploadedEvent;
import com.documind.document.exception.DocumentNotFoundException;
import com.documind.document.repository.DocumentRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Topic provisioning + the consumer retry/recovery policy: 3 retries with
 * exponential backoff (1s, 2s, 4s), then recover by marking the document FAILED
 * in Postgres and publishing the original event to {@code document-events.DLT}.
 */
@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Bean
    public NewTopic documentEventsTopic(@Value("${documind.kafka.document-events-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic documentEventsDltTopic(@Value("${documind.kafka.document-events-dlt-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate,
                                                 DocumentRepository documentRepository) {
        DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
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
        handler.addNotRetryableExceptions(DocumentNotFoundException.class);
        return handler;
    }
}
