package com.org.documind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Document metadata row in Postgres. The raw PDF lives on disk under
 * ./storage, the searchable chunks live in document_chunks — this row tracks
 * identity and pipeline status.
 */
@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DocumentStatus status;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "failure_reason")
    private String failureReason;

    /** JPA requires a no-arg constructor; protected keeps it out of application code. */
    protected DocumentEntity() {
    }

    public DocumentEntity(UUID id, String filename, DocumentStatus status, Instant uploadedAt) {
        this.id = id;
        this.filename = filename;
        this.status = status;
        this.uploadedAt = uploadedAt;
        this.chunkCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}
