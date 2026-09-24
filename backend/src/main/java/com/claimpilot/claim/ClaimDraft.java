package com.claimpilot.claim;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "claim_drafts")
public class ClaimDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimType claimType;

    private UUID policyId;
    private UUID otherPolicyId;
    private UUID receiptId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Relationship relationship;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id")
    private List<ClaimDraftField> fields = new ArrayList<>();

    protected ClaimDraft() {
        // for JPA
    }

    public ClaimDraft(Long ownerId, ClaimType claimType, UUID policyId, UUID otherPolicyId, UUID receiptId,
                      Relationship relationship) {
        this.ownerId = ownerId;
        this.claimType = claimType;
        this.policyId = policyId;
        this.otherPolicyId = otherPolicyId;
        this.receiptId = receiptId;
        this.relationship = relationship;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void addField(DataKey key, DraftValue value) {
        fields.add(new ClaimDraftField(this, key, value));
    }

    public Optional<ClaimDraftField> field(DataKey key) {
        return fields.stream().filter(f -> f.getDataKey() == key).findFirst();
    }

    /** Ready once every field has been reviewed by the member. */
    public boolean fullyReviewed() {
        return fields.stream().allMatch(ClaimDraftField::isReviewed);
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public UUID getOtherPolicyId() {
        return otherPolicyId;
    }

    public UUID getReceiptId() {
        return receiptId;
    }

    public Relationship getRelationship() {
        return relationship;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ClaimDraftField> getFields() {
        return fields;
    }
}
