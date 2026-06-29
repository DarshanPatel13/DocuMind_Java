package com.documind.query;

import com.documind.common.config.DocuMindProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * query-service: the RAG ask flow (hybrid retrieval -&gt; guardrail -&gt;
 * grounded prompt -&gt; streamed SSE answer) plus conversation history in Mongo.
 * Reads the SHARED pgvector store via {@code documind-common}.
 */
@SpringBootApplication(scanBasePackages = "com.documind")
@EnableConfigurationProperties(DocuMindProperties.class)
public class QueryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueryServiceApplication.class, args);
    }
}
