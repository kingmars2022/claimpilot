package com.claimpilot.chat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;

/**
 * "Do I need a predetermination for a $900 plan?" against "for any treatment plan over $500": the
 * answer is a comparison of two numbers, which code does exactly. When the model calls such a
 * question unclear, and the question holds one amount and a clause on the same subject sets a
 * threshold for it, the answer is given by code from that clause. "The same subject" is strict: every
 * word of the question that the retrieved clauses use must be in that clause, so a $900 massage is
 * never measured against the dental rule.
 */
final class ThresholdRule {

    private static final String NUMBER = "(\\d{1,3}(?:[,\\s\\u00a0]\\d{3})+|\\d+)(?:[.,](\\d{2}))?";
    private static final Pattern QUESTION_AMOUNT = Pattern.compile(
            "\\$\\s?" + NUMBER + "|" + NUMBER + "\\s?\\$");
    private static final Pattern THRESHOLD = Pattern.compile("(?i)(?:over|more than|exceeds?|exceeding|above|"
            + "greater than|in excess of|plus de|supérieure? à|excédant|dépassant)\\s+"
            + "(?:\\$\\s?" + NUMBER + "|" + NUMBER + "\\s?\\$)");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.;!?])\\s+");
    private static final Pattern WORD = Pattern.compile("\\p{L}{6,}");
    /** Words too common in a policy to say two sentences are about the same thing. */
    private static final Set<String> COMMON = Set.of("policy", "claims", "insurance", "coverage", "covered",
            "reimbursed", "calendar", "person", "member", "benefit", "benefits", "police", "assurance", "montant");

    /**
     * @param index    the excerpt number, 1-based, as the model saw it
     * @param applies  whether the question's amount is over the threshold
     */
    record Finding(int index, BigDecimal amount, BigDecimal threshold, boolean applies, String sentence) {

        String answer(AnswerLanguage language) {
            String quote = sentence.strip();
            return switch (language) {
                case FRENCH -> applies
                        ? "Oui. " + fr(amount) + " dépasse le seuil de " + fr(threshold)
                                + " de cette clause, donc la règle s'applique : « " + quote + " » [" + index + "]"
                        : "Non. " + fr(amount) + " ne dépasse pas le seuil de " + fr(threshold)
                                + " de cette clause, donc la règle ne s'applique pas : « " + quote + " » ["
                                + index + "]";
                case CHINESE -> applies
                        ? "需要。" + en(amount) + " 超过了该条款 " + en(threshold) + " 的门槛，所以这条规则适用：“" + quote
                                + "” [" + index + "]"
                        : "不需要。" + en(amount) + " 没有超过该条款 " + en(threshold) + " 的门槛，所以这条规则不适用：“"
                                + quote + "” [" + index + "]";
                case ENGLISH -> applies
                        ? "Yes. " + en(amount) + " is over the " + en(threshold)
                                + " threshold in this clause, so the rule applies: \"" + quote + "\" [" + index + "]"
                        : "No. " + en(amount) + " is not over the " + en(threshold)
                                + " threshold in this clause, so the rule does not apply: \"" + quote + "\" ["
                                + index + "]";
            };
        }

        private static String en(BigDecimal value) {
            return "$" + String.format(Locale.CANADA, "%,.2f", value).replace(".00", "");
        }

        private static String fr(BigDecimal value) {
            return String.format(Locale.CANADA_FRENCH, "%,.2f", value).replace(",00", "") + " $";
        }
    }

    private ThresholdRule() {
    }

    static Optional<Finding> find(String question, List<Document> sources) {
        List<BigDecimal> amounts = amounts(QUESTION_AMOUNT.matcher(question));
        if (amounts.size() != 1) {
            return Optional.empty();
        }
        Set<String> questionWords = words(question);
        // The words of the question that the policy uses somewhere ("massage", "dental"): the clause
        // setting the threshold must be about all of them, not just share one.
        Set<String> subject = new java.util.HashSet<>(questionWords);
        subject.retainAll(words(String.join(" ", sources.stream().map(d -> d.getText() == null ? "" : d.getText())
                .toList())));
        for (int i = 0; i < sources.size(); i++) {
            String text = sources.get(i).getText();
            if (text == null || !words(text).containsAll(subject)) {
                continue;
            }
            for (String sentence : SENTENCE_END.split(text.replaceAll("\\s+", " "))) {
                Matcher threshold = THRESHOLD.matcher(sentence);
                if (!threshold.find() || DiscretionaryWording.hedges(sentence)
                        || java.util.Collections.disjoint(words(sentence), questionWords)) {
                    continue;
                }
                BigDecimal limit = number(threshold, 1);
                BigDecimal amount = amounts.getFirst();
                return Optional.of(new Finding(i + 1, amount, limit, amount.compareTo(limit) > 0, sentence));
            }
        }
        return Optional.empty();
    }

    private static List<BigDecimal> amounts(Matcher m) {
        List<BigDecimal> found = new ArrayList<>();
        while (m.find()) {
            found.add(number(m, 1));
        }
        return found;
    }

    /** The amount in the first matched alternative starting at {@code group} (whole, cents). */
    private static BigDecimal number(Matcher m, int group) {
        int whole = m.group(group) != null ? group : group + 2;
        String digits = m.group(whole).replaceAll("[,\\s\\u00a0]", "");
        String cents = m.group(whole + 1);
        return new BigDecimal(cents == null ? digits : digits + "." + cents);
    }

    private static Set<String> words(String text) {
        Set<String> words = new java.util.HashSet<>();
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String word = m.group();
            if (!COMMON.contains(word)) {
                words.add(word.substring(0, 6));
            }
        }
        return words;
    }
}
