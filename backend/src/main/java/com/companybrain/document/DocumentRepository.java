package com.companybrain.document;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();
}
