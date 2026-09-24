package com.claimpilot.chat;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * "May be considered", "at the insurer's discretion": the policy leaves the outcome open. When the
 * answer relies on such a clause, code marks it unclear whatever status the model chose, so the
 * member gets the clause and a call kit instead of a yes.
 * <p>
 * Both sides must hedge: the answer (so an answer that only mentions another sentence of the same
 * chunk is not affected) and one of the clauses it cites (so the model's own caution is not enough).
 */
final class DiscretionaryWording {

    private static final Pattern HEDGE = Pattern.compile(String.join("|",
            "may be (?:considered|covered|eligible|reimbursed|approved)",
            "at the (?:insurer's |plan's |sole )?discretion", "discretionary", "case[- ]by[- ]case",
            "peu(?:t|vent) être (?:considérée?s?|admissibles?|remboursée?s?|couverte?s?)",
            "à la discrétion", "au cas par cas",
            "可能会?被?(?:考虑|报销|承保)", "视情况", "酌情", "个案"));

    private DiscretionaryWording() {
    }

    static boolean decides(String answer, Collection<String> citedClauses) {
        return hedges(answer) && citedClauses.stream().anyMatch(DiscretionaryWording::hedges);
    }

    static boolean hedges(String text) {
        return text != null && HEDGE.matcher(text.toLowerCase(Locale.ROOT)).find();
    }
}
