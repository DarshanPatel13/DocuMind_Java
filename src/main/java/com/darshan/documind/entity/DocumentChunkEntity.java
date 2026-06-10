package com.darshan.documind.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * One chunk of extracted document text.
 *
 * <p>The {@code embedding vector(1536)} column is deliberately NOT mapped:
 * Hibernate has no built-in pgvector type, so inserts go through a native
 * query ({@code DocumentChunkRepository.insertChunk}) that casts a string
 * literal to {@code vector}. The JPA mapping here exists for derived queries
 * (deleteByDocumentId) and plain reads of the text columns.</p>
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    protected DocumentChunkEntity() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public String getContent() {
        return content;
    }
}
