package com.documind.query.service;

import com.documind.common.retrieval.ChunkMatch;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds the grounded system + user messages (Java equivalent of {@code prompt.py}).
 * The grounding contract — answer only from context, cite every claim, return an
 * exact sentinel otherwise — is the core hallucination control.
 */
@Component
public class PromptBuilder {

    /** Exact sentinel the model is told to return when the context has no answer. */
    public static final String NO_INFO_ANSWER = "I don't have enough information in the uploaded documents.";

    private static final String SYSTEM_PROMPT = """
            You are DocuMind, an assistant that answers questions about the user's uploaded documents.
            Answer ONLY from the context provided in the user message.
            Cite sources as [filename, chunk N] after the statements they support.
            If the context does not contain the answer, reply exactly:
            I don't have enough information in the uploaded documents.""";

    private static final String WHOLE_DOC_NOTE =
            "Note: the context below is the COMPLETE document (every chunk, in reading order), "
                    + "not a search result. Use all of it to answer list-all / summarize questions comprehensively.";

    /** Immutable pair of rendered messages for the chat call. */
    public record Messages(String system, String user) {
    }

    public Messages build(String question, List<ChunkMatch> docs, boolean wholeDocument) {
        String context = docs.stream()
                .map(d -> "[%s, chunk %d]%n%s".formatted(d.filename(), d.chunkIndex(), d.content()))
                .collect(Collectors.joining("\n---\n"));
        String prefix = wholeDocument ? WHOLE_DOC_NOTE + "\n\n" : "";
        String user = prefix + "Context:\n" + context + "\n\nQuestion: " + question;
        return new Messages(SYSTEM_PROMPT, user);
    }
}
