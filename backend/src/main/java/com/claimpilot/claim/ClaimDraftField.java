package com.claimpilot.claim;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "claim_draft_fields")
public class ClaimDraftField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "draft_id", nullable = false)
    private ClaimDraft draft;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DataKey dataKey;

    private String value;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    private UUID sourceDocumentId;
    private String sourceLabel;
    private Integer sourcePage;
    private String sourceQuote;
    private boolean verified;
    private boolean reviewed;

    protected ClaimDraftField() {
        // for JPA
    }

    ClaimDraftField(ClaimDraft draft, DataKey dataKey, DraftValue value) {
        this.draft = draft;
        this.dataKey = dataKey;
        this.value = value.value();
        this.sourceType = value.sourceType();
        this.sourceDocumentId = value.sourceDocumentId();
        this.sourceLabel = value.sourceLabel();
        this.sourcePage = value.page();
        this.sourceQuote = value.quote();
        this.verified = value.verified();
    }

    /** The member typed a different value: it becomes theirs and needs no further verification. */
    public void correct(String newValue) {
        String cleaned = newValue == null || newValue.isBlank() ? null : newValue.strip();
        if (java.util.Objects.equals(cleaned, value)) {
            return;
        }
        this.value = cleaned;
        this.sourceType = cleaned == null ? SourceType.MISSING : SourceType.USER;
        this.sourceDocumentId = null;
        this.sourceLabel = cleaned == null ? null : "Entered by you";
        this.sourcePage = null;
        this.sourceQuote = null;
        this.verified = cleaned != null;
    }

    public void setReviewed(boolean reviewed) {
        this.reviewed = reviewed;
    }

    public DataKey getDataKey() {
        return dataKey;
    }

    public String getValue() {
        return value;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public String getSourceLabel() {
        return sourceLabel;
    }

    public Integer getSourcePage() {
        return sourcePage;
    }

    public String getSourceQuote() {
        return sourceQuote;
    }

    public boolean isVerified() {
        return verified;
    }

    public boolean isReviewed() {
        return reviewed;
    }
}
