package com.claimpilot.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A file a user uploaded: a policy or a receipt. A policy's text also lives as vector chunks in
 * the vector_store table, linked by the "documentId" metadata field.
 */
@Entity
@Table(name = "documents")
public class UploadedDocument {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentKind kind;

    @Column(nullable = false)
    private String fileName;

    private String contentType;

    private long sizeBytes;

    @Column(nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    private Integer chunkCount;

    private String errorMessage;
    private String warning;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant processedAt;

    protected UploadedDocument() {
        // for JPA
    }

    public UploadedDocument(Long ownerId, DocumentKind kind, String fileName, String contentType, long sizeBytes,
                            String storageKey) {
        this.ownerId = ownerId;
        this.kind = kind;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = Instant.now();
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        this.errorMessage = null;
        this.warning = null;
    }

    public void markReady(Integer chunkCount) {
        markReady(chunkCount, null);
    }

    /** @param warning a note for the member, for example that a receipt was uploaded as a policy */
    public void markReady(Integer chunkCount, String warning) {
        this.warning = warning;
        this.status = DocumentStatus.READY;
        this.chunkCount = chunkCount;
        this.processedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        String text = reason == null ? "Unknown error" : reason;
        this.errorMessage = text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    public UUID getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public DocumentKind getKind() {
        return kind;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public String getWarning() {
        return warning;
    }
}
