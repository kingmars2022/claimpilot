package com.claimpilot.claim;

import java.util.List;
import java.util.UUID;

/**
 * Module 2: the steps for one claim type under one specific policy. Every item cites the clause it
 * came from; items the model could not tie to a clause are dropped.
 *
 * @param deadlines   notice and submission time limits
 * @param documents   what to send with the claim (a checklist)
 * @param submission  where and how to submit
 * @param coverage    what the policy pays for this care
 * @param preApproval whether approval is needed before the care
 */
public record ClaimGuide(
        UUID policyId,
        ClaimType claimType,
        String claimTypeLabel,
        List<Item> deadlines,
        List<Item> documents,
        List<Item> submission,
        List<Item> coverage,
        PreApproval preApproval,
        boolean found) {

    /**
     * @param page    policy page of the supporting clause, when known
     * @param section policy section of the supporting clause, when known
     * @param clause  the supporting clause text (shortened)
     */
    public record Item(String text, Integer page, String section, String clause) {
    }

    public enum Requirement {
        YES,
        NO,
        UNKNOWN
    }

    public record PreApproval(Requirement required, Item basis) {
    }
}
