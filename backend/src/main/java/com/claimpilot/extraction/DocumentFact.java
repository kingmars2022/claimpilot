package com.claimpilot.extraction;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_facts")
public class DocumentFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fact_key", nullable = false)
    private FactKey key;

    @Column(nullable = false)
    private String value;

    private String quote;

    private Integer page;

    private boolean verified;

    protected DocumentFact() {
        // for JPA
    }

    public DocumentFact(UUID documentId, ExtractedFact fact) {
        this.documentId = documentId;
        this.key = fact.key();
        this.value = fact.value();
        this.quote = fact.quote();
        this.page = fact.page();
        this.verified = fact.verified();
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public FactKey getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public String getQuote() {
        return quote;
    }

    public Integer getPage() {
        return page;
    }

    public boolean isVerified() {
        return verified;
    }
}
