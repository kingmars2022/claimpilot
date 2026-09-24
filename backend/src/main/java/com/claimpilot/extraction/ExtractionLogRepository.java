package com.claimpilot.extraction;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExtractionLogRepository extends MongoRepository<ExtractionLog, String> {

    void deleteByDocumentId(UUID documentId);

    void deleteByOwnerId(Long ownerId);
}
