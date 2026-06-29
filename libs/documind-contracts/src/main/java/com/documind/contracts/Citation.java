package com.documind.contracts;

/**
 * One source reference shown under an answer: the file, the chunk index, and a
 * short snippet for the UI's source-preview. Serialized as
 * {@code {filename, chunk_index, snippet}}.
 */
public record Citation(
        String filename,
        int chunkIndex,
        String snippet
) {
}
