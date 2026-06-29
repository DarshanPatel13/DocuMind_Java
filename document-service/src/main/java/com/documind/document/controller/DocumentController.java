package com.documind.document.controller;

import com.documind.contracts.DocumentResponse;
import com.documind.contracts.UploadResponse;
import com.documind.document.service.DocumentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Upload + list endpoints. Sits behind the gateway (which adds auth/CORS). */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** 202 Accepted: the upload is durable, but ingestion happens asynchronously via Kafka. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.accepted().body(documentService.upload(file));
    }

    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.list();
    }
}
