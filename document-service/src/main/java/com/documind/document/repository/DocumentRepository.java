package com.documind.document.repository;

import com.documind.document.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Document metadata access (Spring Data JPA over the {@code documents} table). */
public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

    List<DocumentEntity> findAllByOrderByUploadedAtDesc();
}
