package com.darshan.documind.service;

import com.darshan.documind.config.DocuMindProperties;
import com.darshan.documind.dto.AskRequest;
import com.darshan.documind.dto.AskResponse;
import com.darshan.documind.dto.Citation;
import com.darshan.documind.entity.ConversationTurn;
import com.darshan.documind.repository.ChunkMatch;
import com.darshan.documind.repository.ConversationTurnRepository;
import com.darshan.documind.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Grounding behaviour and retrieval routing, with every collaborator mocked. */
class AskServiceTest {

    private EmbeddingModel embeddingModel;
    private ChatModel chatModel;
    private DocumentChunkRepository chunkRepository;
    private ConversationTurnRepository conversationRepository;
    private AskService service;

    @BeforeEach
    void setUp() {
        embeddingModel = mock(EmbeddingModel.class);
        chatModel = mock(ChatModel.class);
        chunkRepository = mock(DocumentChunkRepository.class);
        conversationRepository = mock(ConversationTurnRepository.class);
        DocuMindProperties properties = new DocuMindProperties(
                "./storage",
                new DocuMindProperties.Chunking(800, 100, 4),
                new DocuMindProperties.Kafka("document-events", "document-events.DLT"),
                new DocuMindProperties.Ask(4),
                new DocuMindProperties.RateLimit(10));
        service = new AskService(embeddingModel, chatModel, chunkRepository,
                conversationRepository, properties);

        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
    }

    @Test
    void noRetrievedChunksShortCircuitsWithoutCallingTheLlm() {
        when(chunkRepository.findNearest(anyString(), anyInt())).thenReturn(List.of());

        AskResponse response = service.ask(new AskRequest("What is the refund policy?", null, null));

        assertEquals(AskService.NO_INFO_ANSWER, response.answer());
        assertTrue(response.citations().isEmpty());
        assertNotNull(response.conversationId());
        // The grounding guard: no context means the chat model is never invoked...
        verifyNoInteractions(chatModel);
        // ...but the miss is still recorded in the conversation history.
        verify(conversationRepository).save(any(ConversationTurn.class));
    }

    @Test
    void retrievalSendsTheVectorLiteralAndConfiguredTopK() {
        when(chunkRepository.findNearest(anyString(), anyInt())).thenReturn(List.of());

        service.ask(new AskRequest("anything", null, null));

        // The query embedding must reach pgvector as a vector literal, with top-k from config.
        verify(chunkRepository).findNearest("[0.1,0.2,0.3]", 4);
        verify(chunkRepository, never()).findNearestInDocument(anyString(), any(), anyInt());
    }

    @Test
    void documentIdFilterRoutesToTheScopedQuery() {
        UUID documentId = UUID.randomUUID();
        when(chunkRepository.findNearestInDocument(anyString(), eq(documentId), anyInt()))
                .thenReturn(List.of());

        service.ask(new AskRequest("anything", documentId, null));

        verify(chunkRepository).findNearestInDocument("[0.1,0.2,0.3]", documentId, 4);
        verify(chunkRepository, never()).findNearest(anyString(), anyInt());
    }

    @Test
    void answersFromRetrievedChunksWithCitationsAndPersistsTheTurn() {
        UUID chunkId = UUID.randomUUID();
        when(chunkRepository.findNearest(anyString(), anyInt())).thenReturn(List.of(
                match(chunkId, 2, "Refunds are issued within 30 days of purchase.", "policy.pdf")));
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage(
                        "Refunds are issued within 30 days [policy.pdf, chunk 2].")))));

        AskResponse response = service.ask(
                new AskRequest("What is the refund policy?", null, "conv-42"));

        assertEquals("Refunds are issued within 30 days [policy.pdf, chunk 2].", response.answer());
        assertEquals(List.of(new Citation("policy.pdf", 2)), response.citations());
        assertEquals("conv-42", response.conversationId());

        // The grounded prompt must actually carry the retrieved chunk text and its label.
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        String userMessage = prompt.getValue().getInstructions().get(1).getText();
        assertTrue(userMessage.contains("Refunds are issued within 30 days of purchase."));
        assertTrue(userMessage.contains("[policy.pdf, chunk 2]"));

        ArgumentCaptor<ConversationTurn> turn = ArgumentCaptor.forClass(ConversationTurn.class);
        verify(conversationRepository).save(turn.capture());
        assertEquals("conv-42", turn.getValue().getConversationId());
        assertEquals(List.of(chunkId.toString()), turn.getValue().getRetrievedChunkIds());
    }

    private static ChunkMatch match(UUID chunkId, int index, String content, String filename) {
        UUID documentId = UUID.randomUUID();
        return new ChunkMatch() {
            @Override public UUID getChunkId() { return chunkId; }
            @Override public UUID getDocumentId() { return documentId; }
            @Override public Integer getChunkIndex() { return index; }
            @Override public String getContent() { return content; }
            @Override public String getFilename() { return filename; }
            @Override public Double getDistance() { return 0.12; }
        };
    }
}
