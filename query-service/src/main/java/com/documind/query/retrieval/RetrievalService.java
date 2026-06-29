package com.documind.query.retrieval;

import com.documind.common.config.DocuMindProperties;
import com.documind.common.retrieval.ChunkMatch;
import com.documind.common.retrieval.PgVectorStore;
import com.documind.common.retrieval.Rrf;
import com.documind.common.retrieval.VectorLiterals;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Hybrid retrieval orchestration (Java equivalent of {@code retrieval.py}'s
 * {@code retrieve}). Embeds the query with the configured {@link EmbeddingModel},
 * runs the vector + keyword arms against the shared {@link PgVectorStore}, and
 * fuses them with {@link Rrf}. Whole-document mode reads every chunk of one
 * document for list-all / summarize questions.
 */
@Service
public class RetrievalService {

    private final EmbeddingModel embeddingModel;
    private final PgVectorStore vectorStore;
    private final DocuMindProperties properties;

    public RetrievalService(EmbeddingModel embeddingModel, PgVectorStore vectorStore, DocuMindProperties properties) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    /** Top-k chunks for a focused question: hybrid (vector + keyword + RRF) or vector-only. */
    public List<ChunkMatch> retrieve(String query, UUID documentId) {
        DocuMindProperties.Retrieval r = properties.getRetrieval();
        String embedding = VectorLiterals.toVectorLiteral(embeddingModel.embed(query));

        if (r.isHybridEnabled()) {
            List<ChunkMatch> vector = vectorStore.vectorSearch(embedding, documentId, r.getCandidates());
            List<ChunkMatch> keyword = vectorStore.keywordSearch(query, documentId, r.getCandidates());
            return Rrf.fuse(List.of(vector, keyword)).stream().limit(r.getTopK()).toList();
        }
        return vectorStore.vectorSearch(embedding, documentId, r.getTopK());
    }

    /** Whole-document mode: all chunks of the scoped (or single most-relevant) document. */
    public List<ChunkMatch> wholeDocument(String query, UUID documentId) {
        UUID target = documentId;
        if (target == null) {
            String embedding = VectorLiterals.toVectorLiteral(embeddingModel.embed(query));
            List<ChunkMatch> top = vectorStore.vectorSearch(embedding, null, 1);
            if (top.isEmpty()) {
                return List.of();
            }
            target = top.get(0).documentId();
        }
        return vectorStore.fetchDocumentChunks(target, properties.getRetrieval().getWholeDocumentLimit());
    }
}
