package com.claimpilot.chat;

public enum AnswerStatus {
    /** The policy answers the question; the answer cites the clauses. */
    ANSWERED,
    /** Relevant clauses exist but are ambiguous or incomplete: shown with a "confirm with your insurer" note. */
    UNCLEAR,
    /** The policy does not cover the question: the user gets a call kit instead. */
    NOT_IN_POLICY
}
