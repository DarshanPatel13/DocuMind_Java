package com.darshan.documind.entity;

/** Lifecycle of an uploaded document as it moves through the ingestion pipeline. */
public enum DocumentStatus {

    /** Stored on disk and in Postgres; Kafka event published; not yet processed. */
    UPLOADED,

    /** The ingestion consumer is extracting/chunking/embedding it right now. */
    PROCESSING,

    /** Chunks and embeddings are in pgvector; the document is searchable. */
    READY,

    /** Ingestion failed after all retries; see failureReason. Event parked on the DLT. */
    FAILED
}
