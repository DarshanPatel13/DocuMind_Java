package com.documind.document;

import com.documind.common.config.DocuMindProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * document-service: owns uploads + the async Kafka ingestion pipeline.
 * {@code scanBasePackages = "com.documind"} so the shared {@code documind-common}
 * beans (PgVectorStore, CorrelationIdFilter) are component-scanned too.
 */
@SpringBootApplication(scanBasePackages = "com.documind")
@EnableConfigurationProperties(DocuMindProperties.class)
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }
}
