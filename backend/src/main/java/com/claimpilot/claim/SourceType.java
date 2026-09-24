package com.claimpilot.claim;

/** Where a pre-filled value came from. */
public enum SourceType {
    /** The plan being claimed on. */
    POLICY,
    /** The patient's other plan, which paid first. */
    OTHER_POLICY,
    RECEIPT,
    PROFILE,
    /** Chosen when the claim was started, for example the relationship to the plan member. */
    CLAIM_SETUP,
    /** Worked out by code from other fields. */
    CALCULATED,
    /** Typed or corrected by the member. */
    USER,
    /** Not found anywhere: the member has to fill it in. */
    MISSING
}
