package com.claimpilot.claim;

import java.util.List;

/**
 * Claim types supported so far, all "second policy" claims: the part of a bill that a spouse's
 * group plan can pay after the member's own plan has paid first. Each type lists the searches
 * used to find the relevant clauses in the policy.
 */
public enum ClaimType {

    SECONDARY_PARAMEDICAL("Paramedical care (physiotherapy, massage, chiropractic) on a second plan",
            List.of("physiotherapy massage therapy chiropractor coverage percentage annual maximum per visit",
                    "referral or prescription from a physician required for paramedical practitioners",
                    "licensed or registered practitioner requirements")),

    SECONDARY_DENTAL("Dental care on a second plan",
            List.of("dental coverage basic services preventive cleaning percentage annual maximum",
                    "predetermination treatment plan estimate before dental work",
                    "dental fee guide dentist")),

    SECONDARY_DRUGS("Prescription drugs on a second plan",
            List.of("prescription drug coverage percentage dispensing fee deductible",
                    "drug card pharmacy direct billing prior authorization special authorization"));

    /** Searches shared by every claim type: deadlines, documents, how to submit, other coverage. */
    static final List<String> COMMON_QUERIES = List.of(
            "claim submission deadline time limit months after the date of service",
            "documents required to submit a claim original receipts explanation of benefits",
            "how to submit a claim online mobile app mail address",
            "coordination of benefits other coverage spouse plan pays first second claim");

    private final String label;
    private final List<String> queries;

    ClaimType(String label, List<String> queries) {
        this.label = label;
        this.queries = queries;
    }

    public String label() {
        return label;
    }

    public List<String> queries() {
        return queries;
    }
}
