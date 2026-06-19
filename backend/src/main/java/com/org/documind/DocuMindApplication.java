package com.org.documind;

import com.org.documind.config.DocuMindProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * DocuMind — RAG document Q&A service.
 *
 * <p>{@code @ConfigurationPropertiesScan} picks up
 * {@link DocuMindProperties} so every app-specific
 * setting is bound once, type-safely, from application.yml.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DocuMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocuMindApplication.class, args);
    }
}
