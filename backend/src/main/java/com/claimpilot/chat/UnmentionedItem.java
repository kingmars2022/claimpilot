package com.claimpilot.chat;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;

/**
 * "Is acupuncture covered?" when no clause found for the policy says "acupuncture": the policy
 * does not address it, whatever else the clauses say. When the model calls such a question unclear,
 * code makes it "not in the policy", so the member gets the call kit.
 * <p>
 * Kept narrow: English questions only (a French question may name the item in other words than an
 * English policy), coverage questions only, and never when an exclusion clause was found, since
 * the item may fall under one of its categories.
 */
final class UnmentionedItem {

    private static final Pattern COVERAGE_QUESTION = Pattern.compile(
            "(?i)\\bcover|\\breimburs|\\bpay for\\b|\\beligible\\b|\\binclude");
    private static final Pattern EXCLUSION = Pattern.compile(
            "(?i)exclu|not covered|does not cover|not eligible");
    private static final Pattern WORD = Pattern.compile("[a-z]{4,}");
    /** Words of the question that are not the item asked about. */
    private static final Set<String> NOT_THE_ITEM = Set.of(
            "does", "will", "would", "could", "should", "what", "when", "which", "where", "much", "many", "have",
            "with", "this", "that", "there", "their", "your", "from", "about", "under", "also", "still", "need",
            "plan", "plans", "policy", "cover", "covered", "covers", "coverage", "include", "included", "includes",
            "reimbursed", "reimburse", "reimbursement", "eligible", "insurance", "benefit", "benefits", "claim",
            "claims", "paid", "treatment", "treatments", "service", "services", "care", "therapy", "visit",
            "visits", "cost", "costs", "expense", "expenses", "daughter", "child", "children", "kids", "wife",
            "husband", "spouse", "partner", "family", "myself", "mine", "they", "them", "anything", "some");

    private UnmentionedItem() {
    }

    static boolean applies(String question, AnswerLanguage language, List<Document> sources) {
        if (language != AnswerLanguage.ENGLISH || !COVERAGE_QUESTION.matcher(question).find()) {
            return false;
        }
        List<String> items = WORD.matcher(question.toLowerCase(Locale.ROOT)).results()
                .map(r -> r.group()).filter(w -> !NOT_THE_ITEM.contains(w)).toList();
        if (items.isEmpty()) {
            return false;
        }
        String clauses = String.join(" ", sources.stream().map(Document::getText)
                .map(t -> t == null ? "" : t).toList()).toLowerCase(Locale.ROOT);
        if (EXCLUSION.matcher(clauses).find()) {
            return false;
        }
        // Compared by the first five letters, so "acupuncturist" still names "acupuncture".
        return items.stream().noneMatch(item -> clauses.contains(item.substring(0, Math.min(5, item.length()))));
    }
}
