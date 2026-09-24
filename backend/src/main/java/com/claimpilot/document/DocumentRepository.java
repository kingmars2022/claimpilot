package com.claimpilot.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<UploadedDocument, UUID> {

    List<UploadedDocument> findByOwnerIdAndKindOrderByCreatedAtDesc(Long ownerId, DocumentKind kind);

    Optional<UploadedDocument> findByIdAndOwnerId(UUID id, Long ownerId);

    List<UploadedDocument> findByOwnerId(Long ownerId);
}
