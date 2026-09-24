package com.claimpilot.document;

import java.util.UUID;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * Data isolation inside vector search. Every chunk carries its owner's id, and every search is
 * filtered by the signed-in user (and usually by one policy), so text from another person's
 * policy is never retrieved and never reaches the model.
 */
public final class OwnerScope {

    public static final String META_OWNER_ID = "ownerId";

    private OwnerScope() {
    }

    /** Chunks of one policy, and only if it belongs to this user. */
    public static Filter.Expression policy(Long ownerId, UUID policyId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        return b.and(
                b.eq(META_OWNER_ID, ownerId.toString()),
                b.eq(ProcessingService.META_DOCUMENT_ID, policyId.toString())).build();
    }

    /** Every chunk this user owns, for example to delete them all. */
    public static Filter.Expression owner(Long ownerId) {
        return new FilterExpressionBuilder().eq(META_OWNER_ID, ownerId.toString()).build();
    }
}
