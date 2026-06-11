package com.darshan.documind.service;

import com.darshan.documind.config.DocuMindProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chunking maths with the production settings: 800 tokens * 4 chars = 3200-char
 * chunks, 100 tokens * 4 = 400-char overlap, so each window starts 2800 chars
 * after the previous one.
 */
class TextChunkerTest {

    private static final int CHUNK_CHARS = 3200;
    private static final int OVERLAP_CHARS = 400;

    private final TextChunker chunker = new TextChunker(properties());

    private static DocuMindProperties properties() {
        return new DocuMindProperties(
                "./storage",
                new DocuMindProperties.Chunking(800, 100, 4),
                new DocuMindProperties.Kafka("document-events", "document-events.DLT"),
                new DocuMindProperties.Ask(4),
                new DocuMindProperties.RateLimit(10));
    }

    /** Deterministic text: the character at index i is always ('a' + i % 26). */
    private static String text(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append((char) ('a' + i % 26));
        }
        return sb.toString();
    }

    @Test
    void emptyTextProducesNoChunks() {
        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("   ").isEmpty());
    }

    @Test
    void shortTextIsASingleChunk() {
        List<String> chunks = chunker.chunk("hello world");
        assertEquals(1, chunks.size());
        assertEquals("hello world", chunks.get(0));
    }

    @Test
    void chunksNeverExceedTheConfiguredSize() {
        List<String> chunks = chunker.chunk(text(10_000));
        // Windows start at 0, 2800, 5600 and 8400 -> exactly 4 chunks.
        assertEquals(4, chunks.size());
        chunks.forEach(c -> assertTrue(c.length() <= CHUNK_CHARS,
                "no chunk may exceed " + CHUNK_CHARS + " chars"));
        assertEquals(CHUNK_CHARS, chunks.get(0).length());
    }

    @Test
    void consecutiveChunksShareTheOverlapRegion() {
        List<String> chunks = chunker.chunk(text(10_000));
        for (int i = 0; i < chunks.size() - 1; i++) {
            String tailOfCurrent = chunks.get(i).substring(chunks.get(i).length() - OVERLAP_CHARS);
            String headOfNext = chunks.get(i + 1).substring(0, OVERLAP_CHARS);
            assertEquals(tailOfCurrent, headOfNext,
                    "chunk " + i + " must share its last " + OVERLAP_CHARS + " chars with chunk " + (i + 1));
        }
    }

    @Test
    void chunksReassembleToTheOriginalText() {
        String original = text(10_000);
        List<String> chunks = chunker.chunk(original);
        StringBuilder reassembled = new StringBuilder(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            reassembled.append(chunks.get(i).substring(OVERLAP_CHARS));   // drop the duplicated overlap
        }
        // Nothing lost, nothing duplicated beyond the intended overlap.
        assertEquals(original, reassembled.toString());
    }
}
