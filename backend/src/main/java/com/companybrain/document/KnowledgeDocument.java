package com.companybrain.document;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import com.companybrain.user.Department;

/**
 * A file in the knowledge base. Its text lives as vector chunks in the vector_store table,
 * linked by the "documentId" metadata field.
 */
@Entity
@Table(name = "documents")
public class KnowledgeDocument {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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

    @Column(nullable = false)
    private Instant createdAt;

    private Instant indexedAt;

    /** Departments that may see this document; empty means the whole company. */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "document_departments",
            joinColumns = @JoinColumn(name = "document_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id"))
    private Set<Department> departments = new HashSet<>();

    protected KnowledgeDocument() {
        // for JPA
    }

    public KnowledgeDocument(String fileName, String contentType, long sizeBytes, String storageKey,
                             Collection<Department> departments) {
        this.departments = new HashSet<>(departments);
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.status = DocumentStatus.UPLOADED;
        this.createdAt = Instant.now();
    }

    public void restrictTo(Collection<Department> departments) {
        this.departments.clear();
        this.departments.addAll(departments);
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        this.errorMessage = null;
    }

    public void markIndexed(int chunkCount) {
        this.status = DocumentStatus.INDEXED;
        this.chunkCount = chunkCount;
        this.indexedAt = Instant.now();
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        String text = reason == null ? "Unknown error" : reason;
        this.errorMessage = text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }

    public UUID getId() {
        return id;
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

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public Set<Department> getDepartments() {
        return departments;
    }
}
