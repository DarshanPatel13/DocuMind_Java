package com.org.documind.controller;

import com.org.documind.dto.DocumentResponse;
import com.org.documind.dto.UploadResponse;
import com.org.documind.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Upload PDFs and track ingestion status")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** 202 Accepted: the upload is durable, but ingestion happens asynchronously via Kafka. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PDF (max 20 MB)",
            description = "Stores the file and ingests it asynchronously. "
                    + "Poll GET /api/documents until status is READY, then ask questions.")
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.accepted().body(documentService.upload(file));
    }

    @GetMapping
    @Operation(summary = "List all documents with status and chunk count")
    public List<DocumentResponse> list() {
        return documentService.list();
    }
}
