package com.darshan.documind.exception;

/**
 * Thrown anywhere in the ingestion pipeline. Propagates out of the Kafka
 * listener and triggers the retry/DLT policy configured in KafkaConfig.
 */
public class IngestionException extends RuntimeException {

    public IngestionException(String message) {
        super(message);
    }

    public IngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
