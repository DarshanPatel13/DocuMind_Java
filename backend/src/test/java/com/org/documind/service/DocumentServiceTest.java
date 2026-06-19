package com.org.documind.service;

import com.org.documind.config.DocuMindProperties;
import com.org.documind.dto.DocumentUploadedEvent;
import com.org.documind.dto.UploadResponse;
import com.org.documind.entity.DocumentEntity;
import com.org.documind.entity.DocumentStatus;
import com.org.documind.exception.InvalidFileException;
import com.org.documind.kafka.DocumentEventProducer;
import com.org.documind.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Upload validation and the store-metadata-publish sequence. */
class DocumentServiceTest {

    @TempDir
    Path tempDir;

    private DocumentRepository documentRepository;
    private DocumentEventProducer eventProducer;
    private DocumentService service;

    @BeforeEach
    void setUp() {
        documentRepository = mock(DocumentRepository.class);
        eventProducer = mock(DocumentEventProducer.class);
        DocuMindProperties properties = new DocuMindProperties(
                tempDir.toString(),
                new DocuMindProperties.Chunking(800, 100, 4),
                new DocuMindProperties.Kafka("document-events", "document-events.DLT"),
                new DocuMindProperties.Ask(4),
                new DocuMindProperties.RateLimit(10));
        service = new DocumentService(documentRepository, eventProducer, properties);
    }

    @Test
    void rejectsNonPdfFiles() {
        MockMultipartFile txt = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello".getBytes());

        assertThrows(InvalidFileException.class, () -> service.upload(txt));
        verifyNoInteractions(documentRepository, eventProducer);
    }

    @Test
    void rejectsFilesOverTwentyMegabytes() {
        // A mock avoids allocating a real 20 MB payload just to test the guard.
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(20L * 1024 * 1024 + 1);

        assertThrows(InvalidFileException.class, () -> service.upload(oversized));
        verifyNoInteractions(documentRepository, eventProducer);
    }

    @Test
    void rejectsPdfExtensionWithNonPdfContent() {
        // Right name and content type, wrong bytes: the %PDF magic-byte check must catch it.
        MockMultipartFile fake = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "definitely not a pdf".getBytes());

        assertThrows(InvalidFileException.class, () -> service.upload(fake));
        verifyNoInteractions(documentRepository, eventProducer);
    }

    @Test
    void validUploadStoresFileSavesMetadataAndPublishesEvent() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", "%PDF-1.7 minimal content".getBytes());

        UploadResponse response = service.upload(pdf);

        assertNotNull(response.documentId());
        assertEquals(DocumentStatus.UPLOADED.name(), response.status());
        assertTrue(Files.exists(tempDir.resolve(response.documentId() + ".pdf")),
                "the raw file must be stored under <storage>/<documentId>.pdf");

        ArgumentCaptor<DocumentEntity> saved = ArgumentCaptor.forClass(DocumentEntity.class);
        verify(documentRepository).save(saved.capture());
        assertEquals("report.pdf", saved.getValue().getFilename());
        assertEquals(DocumentStatus.UPLOADED, saved.getValue().getStatus());

        ArgumentCaptor<DocumentUploadedEvent> event = ArgumentCaptor.forClass(DocumentUploadedEvent.class);
        verify(eventProducer).publish(event.capture());
        assertEquals(response.documentId(), event.getValue().documentId());
        assertEquals("report.pdf", event.getValue().filename());
    }
}
