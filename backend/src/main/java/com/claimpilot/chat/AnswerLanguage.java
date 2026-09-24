package com.claimpilot.chat;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The language to answer in: the language of the question. Policies in Quebec are often in French
 * while the member may prefer English or Chinese, so the answer language is independent of the
 * policy language.
 */
public enum AnswerLanguage {

    ENGLISH("en", "English",
            "Your policy doesn't answer this. The quickest way to find out is to ask your insurer; "
                    + "here is what you need for the call."),
    FRENCH("fr", "French",
            "Votre police ne répond pas à cette question. Le plus simple est de demander à votre assureur ; "
                    + "voici ce qu'il vous faut pour l'appel."),
    CHINESE("zh", "Simplified Chinese",
            "您的保单里没有这个问题的答案。最快的办法是直接问保险公司，下面是打电话需要的信息。");

    private static final Pattern HAN = Pattern.compile("\\p{IsHan}");
    private static final Pattern FRENCH_ACCENTS = Pattern.compile("[éèêëàâçîïôûùœ]");
    private static final Set<String> FRENCH_WORDS = Set.of(
            "le", "la", "les", "des", "du", "est", "que", "qui", "pour", "mon", "ma", "mes", "une", "combien",
            "quel", "quelle", "quels", "est-ce", "je", "suis", "puis-je", "remboursé", "remboursés", "avec",
            "dans", "sur", "pas", "ou", "mais", "comment", "pourquoi", "assurance", "soins", "votre", "notre");

    private final String code;
    private final String englishName;
    private final String notInPolicyMessage;

    AnswerLanguage(String code, String englishName, String notInPolicyMessage) {
        this.code = code;
        this.englishName = englishName;
        this.notInPolicyMessage = notInPolicyMessage;
    }

    /** Chinese by script; French by accents or common words; English otherwise. */
    public static AnswerLanguage detect(String question) {
        if (question == null || question.isBlank()) {
            return ENGLISH;
        }
        if (HAN.matcher(question).find()) {
            return CHINESE;
        }
        String lower = question.toLowerCase(Locale.ROOT);
        long frenchWords = java.util.Arrays.stream(lower.split("[^\\p{L}'-]+"))
                .map(w -> w.replaceAll("^[a-z]'", ""))
                .filter(FRENCH_WORDS::contains)
                .count();
        if (frenchWords >= 2 || (FRENCH_ACCENTS.matcher(lower).find() && frenchWords >= 1)) {
            return FRENCH;
        }
        return ENGLISH;
    }

    public String code() {
        return code;
    }

    public String englishName() {
        return englishName;
    }

    /** Fixed text, never generated, shown when the policy does not answer the question. */
    public String notInPolicyMessage() {
        return notInPolicyMessage;
    }
}
