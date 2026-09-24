package com.claimpilot.claim;

import java.util.UUID;

/**
 * A pre-filled value and its origin.
 *
 * @param sourceLabel human-readable origin, for example "spouse-policy.pdf, page 1"
 * @param verified    false when the value came from the model and could not be matched to the
 *                    document text; the member is warned to check it
 */
public record DraftValue(
        String value,
        SourceType sourceType,
        UUID sourceDocumentId,
        String sourceLabel,
        Integer page,
        String quote,
        boolean verified) {

    public static DraftValue missing() {
        return new DraftValue(null, SourceType.MISSING, null, null, null, null, false);
    }
}
