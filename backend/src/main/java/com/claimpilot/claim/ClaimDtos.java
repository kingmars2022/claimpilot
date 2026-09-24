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
     */
    public record CreateDraft(
            @NotNull ClaimType claimType,
            @NotNull UUID policyId,
            UUID otherPolicyId,
            UUID receiptId,
            @NotNull Relationship relationship) {
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
            Relationship relationship,
            List<Field> fields,
            List<String> leftForYou,
            boolean readyToDownload,
            Instant createdAt,
            Instant updatedAt) {
    }
}
