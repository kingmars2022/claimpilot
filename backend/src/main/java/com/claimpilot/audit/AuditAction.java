package com.claimpilot.audit;

/** Everything recorded in a user's activity log. */
public enum AuditAction {
    SIGNED_IN,
    SIGNED_UP,
    PROFILE_UPDATED,
    DOCUMENT_UPLOADED,
    DOCUMENT_READY,
    DOCUMENT_FAILED,
    DOCUMENT_DELETED,
    CLAIM_CREATED,
    CLAIM_FIELD_CORRECTED,
    CLAIM_DOWNLOADED,
    CLAIM_DELETED,
    ASSISTANT_USED
}
