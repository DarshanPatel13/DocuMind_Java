package com.darshan.documind.service;

import com.darshan.documind.config.DocuMindProperties;
import com.darshan.documind.dto.AskRequest;
import com.darshan.documind.dto.AskResponse;
import com.darshan.documind.dto.Citation;
import com.darshan.documind.entity.ConversationTurn;
import com.darshan.documind.repository.ChunkMatch;
import com.darshan.documind.repository.ConversationTurnRepository;
import com.darshan.documind.repository.DocumentChunkRepository;
import com.darshan.documind.repository.VectorLiterals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The RAG "ask" flow: embed the question → retrieve the nearest chunks from
 * pgvector → build a grounded prompt → call the chat model → persist the turn.
 *
 * <p>Depends only on the {@link ChatModel} and {@link EmbeddingModel}
 * interfaces — which provider implements them is decided entirely in
 * application.yml. That is the whole provider-pluggability story.</p>
 */
@Service
public class AskService {

    /** Exact sentinel the model is instructed to return when the context has no answer. */
    public static final String NO_INFO_ANSWER = "I don't have enough information in the uploaded documents.";

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    /**
     * Grounding rules: answer only from supplied context, cite every claim,
     * and use the exact sentinel when the context falls short. Combined with
     * temperature 0.2 (application.yml), this is the hallucination control.
     */
    private static final String SYSTEM_PROMPT = """
            You are DocuMind, an assistant that answers questions about the user's uploaded documents.
            Answer ONLY from the context provided in the user message.
            Cite sources as [filename, chunk N] after the statements they support.
            If the context does not contain the answer, reply exactly:
            I don't have enough information in the uploaded documents.""";

    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final DocumentChunkRepository chunkRepository;
    private final ConversationTurnRepository conversationRepository;
    private final DocuMindProperties properties;

    public AskService(EmbeddingModel embeddingModel,
                      ChatModel chatModel,
                      DocumentChunkRepository chunkRepository,
                      ConversationTurnRepository conversationRepository,
                      DocuMindProperties properties) {
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.chunkRepository = chunkRepository;
        this.conversationRepository = conversationRepository;
        this.properties = properties;
    }

    public AskResponse ask(AskRequest request) {
        String conversationId = request.conversationId() == null || request.conversationId().isBlank()
                ? UUID.randomUUID().toString()
                : request.conversationId();

        // 1. Embed the question into the same vector space as the chunks.
        float[] questionEmbedding = embeddingModel.embed(request.question());
        String embeddingLiteral = VectorLiterals.toVectorLiteral(questionEmbedding);

        // 2. Cosine similarity search in pgvector, optionally scoped to one document.
        int topK = properties.ask().topK();
        List<ChunkMatch> matches = request.documentId() == null
                ? chunkRepository.findNearest(embeddingLiteral, topK)
                : chunkRepository.findNearestInDocument(embeddingLiteral, request.documentId(), topK);
        log.info("stage=retrieve conversationId={} matches={} bestDistance={}",
                conversationId, matches.size(),
                matches.isEmpty() ? null : matches.get(0).getDistance());

        // 3. Grounding guard: no retrieved context means there is nothing to
        //    answer from — short-circuit with the sentinel instead of paying
        //    for an LLM call that could only improvise.
        if (matches.isEmpty()) {
            persistTurn(conversationId, request.question(), NO_INFO_ANSWER, List.of(), List.of());
            return new AskResponse(NO_INFO_ANSWER, List.of(), conversationId);
        }

        // 4. Grounded prompt: rules in the system message, context + question
        //    in the user message, each chunk labelled exactly the way the
        //    model is told to cite it.
        String userMessage = "Context:\n" + buildContext(matches) + "\n\nQuestion: " + request.question();
        ChatResponse response = chatModel.call(
                new Prompt(List.of(new SystemMessage(SYSTEM_PROMPT), new UserMessage(userMessage))));
        String answer = response.getResult().getOutput().getText();
        log.info("stage=answer conversationId={} answerChars={}", conversationId, answer.length());

        // 5. Persist the turn and return the answer with its provenance.
        List<Citation> citations = matches.stream()
                .map(m -> new Citation(m.getFilename(), m.getChunkIndex()))
                .toList();
        List<String> chunkIds = matches.stream()
                .map(m -> m.getChunkId().toString())
                .toList();
        persistTurn(conversationId, request.question(), answer, citations, chunkIds);

        return new AskResponse(answer, citations, conversationId);
    }

    private String buildContext(List<ChunkMatch> matches) {
        return matches.stream()
                .map(m -> "[%s, chunk %d]%n%s".formatted(m.getFilename(), m.getChunkIndex(), m.getContent()))
                .collect(Collectors.joining("\n---\n"));
    }

    private void persistTurn(String conversationId, String question, String answer,
                             List<Citation> citations, List<String> retrievedChunkIds) {
        conversationRepository.save(new ConversationTurn(
                conversationId, question, answer, citations, retrievedChunkIds, Instant.now()));
    }
}
