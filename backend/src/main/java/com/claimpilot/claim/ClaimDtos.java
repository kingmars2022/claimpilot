package com.claimpilot.claim;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Request and response bodies for the claims API. */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    public record ClaimTypeOption(ClaimType type, String label) {
    }

    /**
     * @param policyId      the plan being claimed on (for a second-plan claim, usually the spouse's)
     * @param otherPolicyId the plan that paid first, if any
     * @param receiptId     the receipt for the care
     * @param formKey       the form to fill (see {@link FormCatalog}); the default Cedarview form if empty
     * @param childId       for a child's claim, which child (the patient); optional with one child
     */
    public record CreateDraft(
            @NotNull ClaimType claimType,
            @NotNull UUID policyId,
            UUID otherPolicyId,
            UUID receiptId,
            @NotNull Relationship relationship,
            @Size(max = 100) String formKey,
            Long childId) {

        public CreateDraft(ClaimType claimType, UUID policyId, UUID otherPolicyId, UUID receiptId,
                           Relationship relationship, String formKey) {
            this(claimType, policyId, otherPolicyId, receiptId, relationship, formKey, null);
        }
    }

    public record FormRef(String key, String name) {
    }

    /** Either a corrected value, a review tick, or both. */
    public record UpdateField(@Size(max = 500) String value, Boolean reviewed) {
    }

    public record Field(
            DataKey key,
            String label,
            String value,
            SourceType sourceType,
            String sourceLabel,
            Integer page,
            String quote,
            boolean verified,
            boolean reviewed) {

        static Field from(ClaimDraftField f) {
            return new Field(f.getDataKey(), f.getDataKey().label(), f.getValue(), f.getSourceType(),
                    f.getSourceLabel(), f.getSourcePage(), f.getSourceQuote(), f.isVerified(), f.isReviewed());
        }
    }

    public record DocumentRef(UUID id, String fileName) {
    }

    /**
     * @param leftForYou form items the member must complete personally (declaration, signature, date)
     */
    public record Draft(
            UUID id,
            ClaimType claimType,
            String claimTypeLabel,
            DocumentRef policy,
            DocumentRef otherPolicy,
            DocumentRef receipt,
            FormRef form,
            Relationship relationship,
            List<Field> fields,
            List<String> leftForYou,
            boolean readyToDownload,
            Instant createdAt,
            Instant updatedAt) {
    }
}
