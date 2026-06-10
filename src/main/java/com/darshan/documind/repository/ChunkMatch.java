package com.darshan.documind.repository;

import java.util.UUID;

/**
 * Interface-based projection for the native similarity-search queries in
 * {@link DocumentChunkRepository}. Spring Data maps the quoted SQL aliases
 * ("chunkId", "filename", ...) onto these getters.
 */
public interface ChunkMatch {

    UUID getChunkId();

    UUID getDocumentId();

    Integer getChunkIndex();

    String getContent();

    String getFilename();

    /**
     * Cosine DISTANCE = 1 - cosine similarity. 0 means the chunk points in
     * exactly the same direction as the question vector; lower = more similar.
     */
    Double getDistance();
}
