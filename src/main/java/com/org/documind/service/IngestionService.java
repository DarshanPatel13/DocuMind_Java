package com.darshan.documind.service;

import com.darshan.documind.dto.DocumentUploadedEvent;
import com.darshan.documind.entity.DocumentEntity;
import com.darshan.documind.entity.DocumentStatus;
import com.darshan.documind.exception.DocumentNotFoundException;
import com.darshan.documind.exception.IngestionException;
import com.darshan.documind.repository.DocumentChunkRepository;
import com.darshan.documind.repository.DocumentRepository;
import com.darshan.documind.repository.VectorLiterals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * The ingestion pipeline: PROCESSING → extract → chunk → embed → store → READY.
 *
 * <p>Runs on the Kafka consumer thread. Deliberately NOT one big
 * {@code @Transactional}: the embeddings API calls can take seconds and must
 * not hold a database transaction open. Each repository call commits on its
 * own, and a crash mid-way is healed by the idempotent re-run (status guard +
 * delete-then-reinsert of chunks).</p>
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    /**
     * OpenAI accepts much larger batches, but 20 chunks per request keeps
     * payloads small and a failed batch cheap to retry.
     */
    private static final int EMBEDDING_BATCH_SIZE = 20;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final PdfTextExtractor pdfTextExtractor;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;

    public IngestionService(DocumentRepository documentRepository,
                            DocumentChunkRepository chunkRepository,
                            PdfTextExtractor pdfTextExtractor,
                            TextChunker textChunker,
                            EmbeddingModel embeddingModel) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.pdfTextExtractor = pdfTextExtractor;
        this.textChunker = textChunker;
        this.embeddingModel = embeddingModel;
    }

    public void ingest(DocumentUploadedEvent event) {
        DocumentEntity document = documentRepository.findById(event.documentId())
                .orElseThrow(() -> new DocumentNotFoundException(event.documentId()));

        // Idempotency guard: Kafka is at-least-once, so the same event can be
        // redelivered (consumer rebalance, restart before the offset commit).
        // Re-processing a READY document would only burn API credits.
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
        log.info("stage=chunked documentId={} chunks={} chars={}",
                document.getId(), chunks.size(), text.length());

        // Clean slate so a re-run after a partial failure cannot duplicate chunks.
        chunkRepository.deleteByDocumentId(document.getId());

        for (int from = 0; from < chunks.size(); from += EMBEDDING_BATCH_SIZE) {
            List<String> batch = chunks.subList(from, Math.min(from + EMBEDDING_BATCH_SIZE, chunks.size()));
            List<float[]> vectors = embeddingModel.embed(batch);
            for (int i = 0; i < batch.size(); i++) {
                chunkRepository.insertChunk(UUID.randomUUID(), document.getId(), from + i,
                        batch.get(i), VectorLiterals.toVectorLiteral(vectors.get(i)));
            }
            log.info("stage=embedded documentId={} batchStart={} batchSize={}",
                    document.getId(), from, batch.size());
        }

        document.setStatus(DocumentStatus.READY);
        document.setChunkCount(chunks.size());
        document.setFailureReason(null);
        documentRepository.save(document);
        log.info("stage=ready documentId={} chunkCount={}", document.getId(), chunks.size());
    }
}
