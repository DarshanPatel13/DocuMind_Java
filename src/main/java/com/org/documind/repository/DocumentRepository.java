package com.darshan.documind.repository;

import com.darshan.documind.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    /** Newest first, for the GET /api/documents listing. */
    List<DocumentEntity> findAllByOrderByUploadedAtDesc();
}
