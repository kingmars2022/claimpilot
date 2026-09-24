package com.claimpilot.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<UploadedDocument, UUID> {

    List<UploadedDocument> findByOwnerIdAndKindOrderByCreatedAtDesc(Long ownerId, DocumentKind kind);

    Optional<UploadedDocument> findByIdAndOwnerId(UUID id, Long ownerId);

    List<UploadedDocument> findByOwnerId(Long ownerId);

    long countByOwnerId(Long ownerId);

    Optional<UploadedDocument> findByStorageKey(String storageKey);

    @Query("select coalesce(sum(d.sizeBytes), 0) from UploadedDocument d where d.ownerId = :ownerId")
    long totalBytesOf(@Param("ownerId") Long ownerId);
}
