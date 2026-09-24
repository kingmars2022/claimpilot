package com.claimpilot.assistant;

/** What the assistant can do, always in this order when several are needed. */
public enum AssistantAction {
    /** Answer a question from the policy (Module 1). */
    ASK,
    /** Show what a claim needs under the policy (Module 2). */
    GUIDE,
    /** Start a claim and pre-fill its form (Module 3). */
    FILL
}
