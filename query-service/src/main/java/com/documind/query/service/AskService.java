package com.documind.query.service;

import com.documind.common.retrieval.ChunkMatch;
import com.documind.contracts.AskRequest;
import com.documind.contracts.Citation;
import com.documind.query.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * The RAG ask use-case, streamed to the browser as Server-Sent Events
 * (Java equivalent of {@code ask_service.py}). Protocol per {@code data:} line:
 * a {@code citations} event first, then many {@code token} events, then {@code done}.
 * The full answer is assembled server-side and persisted after the stream ends.
 *
 * <p>Each request runs on the SSE worker pool: retrieval (blocking) then the
 * streamed chat call, written to the {@link SseEmitter} as tokens arrive.</p>
 */
@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);
    private static final int MAX_CITATIONS = 6;
    private static final int SNIPPET_CHARS = 600;
    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ChatClient chatClient;
    private final RetrievalService retrieval;
    private final Guardrails guardrails;
    private final IntentDetector intent;
    private final PromptBuilder promptBuilder;
    private final ConversationService conversations;
    private final Executor executor;

    public AskService(ChatModel chatModel,
                      RetrievalService retrieval,
                      Guardrails guardrails,
                      IntentDetector intent,
                      PromptBuilder promptBuilder,
                      ConversationService conversations,
                      @Qualifier("sseExecutor") Executor executor) {
        this.chatClient = ChatClient.create(chatModel);
        this.retrieval = retrieval;
        this.guardrails = guardrails;
        this.intent = intent;
        this.promptBuilder = promptBuilder;
        this.conversations = conversations;
        this.executor = executor;
    }

    public SseEmitter ask(AskRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        executor.execute(() -> {
            try {
                run(request, emitter);
                emitter.complete();
            } catch (Exception e) {
                log.error("stage=ask-error message={}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void run(AskRequest request, SseEmitter emitter) throws IOException {
        String conversationId = (request.conversationId() == null || request.conversationId().isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : request.conversationId();

        // 0. Guardrail: reject prompt-injection before any retrieval / LLM work.
        if (guardrails.isInjection(request.question())) {
            log.warn("stage=guardrail conversationId={} blocked=injection", conversationId);
            send(emitter, Map.of("type", "citations", "conversation_id", conversationId, "citations", List.of()));
            send(emitter, Map.of("type", "token", "token", Guardrails.INJECTION_REFUSAL));
            send(emitter, Map.of("type", "done"));
            conversations.saveTurn(conversationId, request.question(), Guardrails.INJECTION_REFUSAL, List.of(), List.of());
            return;
        }

        // 1. Choose retrieval strategy: whole-document for list-all / summarize, else hybrid top-k.
        boolean wholeDoc = intent.isAggregate(request.question());
        List<ChunkMatch> docs = wholeDoc
                ? retrieval.wholeDocument(request.question(), request.documentId())
                : retrieval.retrieve(request.question(), request.documentId());
        log.info("stage=retrieve conversationId={} matches={} mode={}",
                conversationId, docs.size(), wholeDoc ? "whole_document" : "hybrid");

        List<ChunkMatch> citeDocs = (wholeDoc && docs.size() > MAX_CITATIONS) ? docs.subList(0, MAX_CITATIONS) : docs;
        List<Citation> citations = citeDocs.stream()
                .map(d -> new Citation(d.filename(), d.chunkIndex(), snippet(d.content())))
                .toList();
        List<String> chunkIds = docs.stream().map(d -> d.chunkId().toString()).toList();

        // 2. Citations + conversation id up front so the UI can render chips immediately.
        send(emitter, Map.of("type", "citations", "conversation_id", conversationId, "citations", citations));

        // 3. Grounding guard: no context -> sentinel, WITHOUT calling the LLM.
        String answer;
        if (docs.isEmpty()) {
            answer = PromptBuilder.NO_INFO_ANSWER;
            send(emitter, Map.of("type", "token", "token", answer));
        } else {
            PromptBuilder.Messages messages = promptBuilder.build(request.question(), docs, wholeDoc);
            StringBuilder full = new StringBuilder();
            chatClient.prompt()
                    .system(messages.system())
                    .user(messages.user())
                    .stream()
                    .content()
                    .toStream()
                    .forEach(token -> {
                        if (token != null && !token.isEmpty()) {
                            full.append(token);
                            sendQuietly(emitter, Map.of("type", "token", "token", token));
                        }
                    });
            answer = full.toString();
        }

        send(emitter, Map.of("type", "done"));
        conversations.saveTurn(conversationId, request.question(), answer, citations, chunkIds);
        log.info("stage=answer conversationId={} answerChars={}", conversationId, answer.length());
    }

    private void send(SseEmitter emitter, Object data) throws IOException {
        emitter.send(SseEmitter.event().data(data, MediaType.APPLICATION_JSON));
    }

    private void sendQuietly(SseEmitter emitter, Object data) {
        try {
            send(emitter, data);
        } catch (IOException e) {
            throw new RuntimeException(e);   // breaks the token stream; surfaced via completeWithError
        }
    }

    private String snippet(String content) {
        return content.length() <= SNIPPET_CHARS ? content : content.substring(0, SNIPPET_CHARS);
    }
}
