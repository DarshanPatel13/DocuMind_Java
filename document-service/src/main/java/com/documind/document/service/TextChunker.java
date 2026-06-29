package com.documind.document.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted text into overlapping fixed-size windows (~800 tokens with a
 * ~100-token overlap). Token sizes are estimated as ~4 chars/token — chunking
 * only needs to be roughly right. The overlap guarantees a sentence cut at a
 * boundary still appears whole in the neighbouring chunk.
 */
@Component
public class TextChunker {

    private final int chunkChars;
    private final int overlapChars;

    public TextChunker(
            @Value("${documind.chunking.chunk-size-tokens:800}") int chunkSizeTokens,
            @Value("${documind.chunking.overlap-tokens:100}") int overlapTokens,
            @Value("${documind.chunking.chars-per-token:4}") int charsPerToken) {
        this.chunkChars = chunkSizeTokens * charsPerToken;     // 800 * 4 = 3200
        this.overlapChars = overlapTokens * charsPerToken;     // 100 * 4 = 400
        if (chunkChars <= overlapChars) {
            throw new IllegalArgumentException(
                    "chunk size (%d chars) must exceed overlap (%d chars)".formatted(chunkChars, overlapChars));
        }
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        int step = chunkChars - overlapChars;
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int start = 0; start < length; start += step) {
            int end = Math.min(start + chunkChars, length);
            String piece = text.substring(start, end);
            if (!piece.isBlank()) {
                chunks.add(piece);
            }
            if (end == length) {
                break;
            }
        }
        return chunks;
    }
}
