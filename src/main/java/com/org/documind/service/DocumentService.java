package com.darshan.documind.service;

import com.darshan.documind.config.DocuMindProperties;
import com.darshan.documind.dto.DocumentResponse;
import com.darshan.documind.dto.DocumentUploadedEvent;
import com.darshan.documind.dto.UploadResponse;
import com.darshan.documind.entity.DocumentEntity;
import com.darshan.documind.entity.DocumentStatus;
import com.darshan.documind.exception.InvalidFileException;
import com.darshan.documind.kafka.DocumentEventProducer;
import com.darshan.documind.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Upload handling: validate → store the file → record metadata → publish the
 * ingestion event. Everything slow (extraction, embeddings) happens later, on
 * the Kafka consumer side — the upload request returns in milliseconds.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;

    /** Every real PDF starts with the ASCII bytes "%PDF". */
    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F'};

    private final DocumentRepository documentRepository;
    private final DocumentEventProducer eventProducer;
    private final Path storageDir;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentEventProducer eventProducer,
                           DocuMindProperties properties) {
        this.documentRepository = documentRepository;
        this.eventProducer = eventProducer;
        this.storageDir = Path.of(properties.storageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage directory " + storageDir, e);
        }
    }

    /**
     * Order matters here: file to disk first, then the DB row, then the event —
     * the event must never reference a file or row that does not exist yet.
     */
    public UploadResponse upload(MultipartFile file) {
        validatePdf(file);

        UUID documentId = UUID.randomUUID();
        String filename = sanitizeFilename(file.getOriginalFilename());
        // Stored under the server-generated UUID, never the client's filename —
        // no collisions, no path tricks.
        Path target = storageDir.resolve(documentId + ".pdf");

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store uploaded file", e);
        }

        DocumentEntity document = new DocumentEntity(documentId, filename, DocumentStatus.UPLOADED, Instant.now());
        documentRepository.save(document);

        eventProducer.publish(new DocumentUploadedEvent(
                documentId, filename, target.toString(), document.getUploadedAt()));
        log.info("stage=upload documentId={} filename={} sizeBytes={}", documentId, filename, file.getSize());

        return new UploadResponse(documentId, DocumentStatus.UPLOADED.name(), "Document accepted for processing");
    }

    public List<DocumentResponse> list() {
        return documentRepository.findAllByOrderByUploadedAtDesc().stream()
                .map(d -> new DocumentResponse(d.getId(), d.getFilename(), d.getStatus().name(),
                        d.getUploadedAt(), d.getChunkCount(), d.getFailureReason()))
                .toList();
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("A non-empty 'file' part is required");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("File exceeds the 20 MB limit");
        }
        String name = file.getOriginalFilename();
        boolean pdfName = name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf");
        boolean pdfContentType = MediaType.APPLICATION_PDF_VALUE.equals(file.getContentType());
        if (!pdfName && !pdfContentType) {
            throw new InvalidFileException("Only PDF files are accepted");
        }
        // Extensions and Content-Type headers are client-supplied and trivially
        // spoofed; the %PDF magic bytes are the real test.
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(PDF_MAGIC.length);
            if (!Arrays.equals(header, PDF_MAGIC)) {
                throw new InvalidFileException("File content is not a valid PDF");
            }
        } catch (IOException e) {
            throw new InvalidFileException("Could not read the uploaded file");
        }
    }

    /** Browsers may send a full client-side path; keep only the last segment. */
    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) {
            return "document.pdf";
        }
        int lastSeparator = Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\'));
        return lastSeparator >= 0 ? original.substring(lastSeparator + 1) : original;
    }
}
