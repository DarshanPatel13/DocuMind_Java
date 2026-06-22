package com.org.documind.repository;

import com.org.documind.entity.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Chunk storage and cosine similarity search against pgvector.
 *
 * <p>Embeddings cross the JDBC boundary as text literals like
 * {@code "[0.12,-0.03,...]"} cast to {@code vector} in SQL, because Hibernate
 * has no native pgvector type. A 1536-float literal per row is perfectly fine
 * at this scale and keeps the mapping dead simple.</p>
 *
 * <p>{@code <=>} is pgvector's cosine-distance operator. Using it in ORDER BY
 * is exactly what lets the HNSW index (built with vector_cosine_ops in
 * 01-init.sql) accelerate the search.</p>
 */
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO document_chunks (id, document_id, chunk_index, content, embedding)
            VALUES (:id, :documentId, :chunkIndex, :content, CAST(:embedding AS vector))
            """, nativeQuery = true)
    void insertChunk(@Param("id") UUID id,
                     @Param("documentId") UUID documentId,
                     @Param("chunkIndex") int chunkIndex,
                     @Param("content") String content,
                     @Param("embedding") String embedding);

    /** Lets a re-run after a partial failure start clean (consumer idempotency). */
    @Transactional
    void deleteByDocumentId(UUID documentId);

    /** Top-K most similar chunks across ALL documents. */
    @Query(value = """
            SELECT c.id          AS "chunkId",
                   c.document_id AS "documentId",
                   c.chunk_index AS "chunkIndex",
                   c.content     AS "content",
                   d.filename    AS "filename",
                   c.embedding <=> CAST(:queryEmbedding AS vector) AS "distance"
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<ChunkMatch> findNearest(@Param("queryEmbedding") String queryEmbedding,
                                 @Param("topK") int topK);

    /** Same search restricted to one document (the optional documentId filter on /api/ask). */
    @Query(value = """
            SELECT c.id          AS "chunkId",
                   c.document_id AS "documentId",
                   c.chunk_index AS "chunkIndex",
                   c.content     AS "content",
                   d.filename    AS "filename",
                   c.embedding <=> CAST(:queryEmbedding AS vector) AS "distance"
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE c.document_id = :documentId
            ORDER BY c.embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<ChunkMatch> findNearestInDocument(@Param("queryEmbedding") String queryEmbedding,
                                           @Param("documentId") UUID documentId,
                                           @Param("topK") int topK);
}
