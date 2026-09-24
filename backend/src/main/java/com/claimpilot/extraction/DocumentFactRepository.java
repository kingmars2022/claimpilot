package com.claimpilot.extraction;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DocumentFactRepository extends JpaRepository<DocumentFact, Long> {

    List<DocumentFact> findByDocumentId(UUID documentId);

    @Transactional
    void deleteByDocumentId(UUID documentId);
}
