package com.claimpilot.user;

/**
 * Who the member's children live with. Coordination of benefits for a child depends on it: parents
 * living together use the birthday rule, separated or divorced parents follow custody.
 */
public enum Custody {
    /** The child's parents live together (the member and their spouse). */
    TOGETHER,
    /** Separated: the member has custody. */
    SOLE_ME,
    /** Separated: the child's other parent has custody. */
    SOLE_OTHER_PARENT,
    /** Separated: joint (shared) custody. */
    JOINT
}
