package com.org.documind.service;

import com.org.documind.config.DocuMindProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted text into overlapping fixed-size windows.
 *
 * <p><b>Token estimation:</b> embedding and chat models limit and bill by
 * tokens, but exact counting requires the model's own tokenizer (tiktoken).
 * For chunking we only need to be roughly right: English text averages about
 * 4 characters per token, so an 800-token chunk is ~3200 characters. Being
 * 10–20% off is harmless — chunks stay comfortably inside
 * text-embedding-3-small's 8191-token input limit.</p>
 *
 * <p><b>Why overlap:</b> a hard cut can split a sentence (or an idea) across
 * two chunks, leaving neither half meaningful enough to be retrieved.
 * Repeating the last ~100 tokens of each chunk at the start of the next
 * guarantees every passage appears intact in at least one chunk.</p>
 *
 * <p>Deliberately simple: fixed character windows, no sentence-boundary
 * snapping. Production chunkers often snap to sentence or paragraph
 * boundaries (nicer citations, slightly better retrieval) at the cost of less
 * predictable chunk sizes.</p>
 */
@Component
public class TextChunker {

    private final int chunkChars;
    private final int overlapChars;

    public TextChunker(DocuMindProperties properties) {
        DocuMindProperties.Chunking chunking = properties.chunking();
        this.chunkChars = chunking.chunkSizeTokens() * chunking.charsPerToken();   // 800 * 4 = 3200
        this.overlapChars = chunking.overlapTokens() * chunking.charsPerToken();   // 100 * 4 = 400
        if (chunkChars <= overlapChars) {
            throw new IllegalArgumentException(
                    "chunk size (%d chars) must be larger than overlap (%d chars)"
                            .formatted(chunkChars, overlapChars));
        }
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        // Each window starts (chunk - overlap) characters after the previous
        // one, so consecutive chunks share exactly overlapChars characters.
        int step = chunkChars - overlapChars;
        List<String> chunks = new ArrayList<>();
        int length = text.length();
        for (int start = 0; start < length; start += step) {
            int end = Math.min(start + chunkChars, length);
            chunks.add(text.substring(start, end));
            if (end == length) {
                break;   // final window reached the end of the text
            }
        }
        return chunks;
    }
}
