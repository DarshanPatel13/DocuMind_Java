package com.documind.document.service;

import com.documind.common.retrieval.PgVectorStore;
import com.documind.common.retrieval.VectorLiterals;
import com.documind.contracts.DocumentStatus;
import com.documind.contracts.DocumentUploadedEvent;
import com.documind.document.entity.DocumentEntity;
import com.documind.document.exception.DocumentNotFoundException;
import com.documind.document.exception.IngestionException;
import com.documind.document.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * The ingestion pipeline: PROCESSING -&gt; extract -&gt; chunk -&gt; embed -&gt;
 * store -&gt; READY. Runs on the Kafka consumer thread. Idempotent: a READY
 * document is skipped, and chunks are delete-then-reinserted so an at-least-once
 * redelivery is harmless. Embeddings come from whichever provider Spring AI is
 * configured with (OpenAI / Ollama) — this code only sees {@link EmbeddingModel}.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    /** Keeps each embeddings request small and a failed batch cheap to retry. */
    private static final int EMBEDDING_BATCH_SIZE = 20;

    private final DocumentRepository documentRepository;
    private final PgVectorStore vectorStore;
    private final PdfTextExtractor pdfTextExtractor;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;

    public IngestionService(DocumentRepository documentRepository,
                            PgVectorStore vectorStore,
                            PdfTextExtractor pdfTextExtractor,
                            TextChunker textChunker,
                            EmbeddingModel embeddingModel) {
        this.documentRepository = documentRepository;
        this.vectorStore = vectorStore;
        this.pdfTextExtractor = pdfTextExtractor;
        this.textChunker = textChunker;
        this.embeddingModel = embeddingModel;
    }

    public void ingest(DocumentUploadedEvent event) {
        DocumentEntity document = documentRepository.findById(event.documentId())
                .orElseThrow(() -> new DocumentNotFoundException(event.documentId()));

        if (document.getStatus() == DocumentStatus.READY) {
            log.info("stage=skip documentId={} reason=already-ready", document.getId());
            return;
        }

        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        log.info("stage=processing documentId={}", document.getId());

        String text = pdfTextExtractor.extract(Path.of(event.storagePath()));
        if (text.isBlank()) {
            throw new IngestionException("No extractable text in document " + document.getId()
                    + " (scanned/image-only PDF?)");
        }

        List<String> chunks = textChunker.chunk(text);
        log.info("stage=chunked documentId={} chunks={} chars={}", document.getId(), chunks.size(), text.length());

        // Clean slate so a re-run after a partial failure cannot duplicate chunks.
        vectorStore.deleteByDocumentId(document.getId());

        for (int from = 0; from < chunks.size(); from += EMBEDDING_BATCH_SIZE) {
            List<String> batch = chunks.subList(from, Math.min(from + EMBEDDING_BATCH_SIZE, chunks.size()));
            List<float[]> vectors = embeddingModel.embed(batch);
            for (int i = 0; i < batch.size(); i++) {
                vectorStore.insertChunk(UUID.randomUUID(), document.getId(), from + i,
                        batch.get(i), VectorLiterals.toVectorLiteral(vectors.get(i)));
            }
            log.info("stage=embedded documentId={} batchStart={} batchSize={}", document.getId(), from, batch.size());
        }

        document.setStatus(DocumentStatus.READY);
        document.setChunkCount(chunks.size());
        document.setFailureReason(null);
        documentRepository.save(document);
        log.info("stage=ready documentId={} chunkCount={}", document.getId(), chunks.size());
    }
}
