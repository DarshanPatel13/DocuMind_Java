package com.documind.common.retrieval;

import java.util.UUID;

/**
 * One retrieved chunk and how it scored. {@code distance} is pgvector cosine
 * distance for the vector arm (lower = closer); the keyword arm and
 * whole-document reads leave it at 0. Identity for de-duplication/fusion is
 * {@code chunkId}.
 */
public record ChunkMatch(
        UUID chunkId,
        UUID documentId,
        int chunkIndex,
        String content,
        String filename,
        double distance
) {
}
