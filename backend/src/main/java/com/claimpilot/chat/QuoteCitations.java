package com.claimpilot.chat;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;

/**
 * The model sometimes quotes a clause word for word but puts the wrong excerpt number after it, so
 * the member is sent to a page that does not hold the sentence. Code checks every quote against the
 * excerpt it cites and, when the quote is in another excerpt instead, points the citation there.
 */
final class QuoteCitations {

    /** A quote of at least a few words, in straight or curly quotes, followed by its citations. */
    private static final Pattern QUOTE = Pattern.compile(
            "[\"“«]\\s*([^\"”»]{15,}?)\\s*[\"”»]\\s*((?:\\[\\d{1,2}])+)");
    private static final Pattern NUMBER = Pattern.compile("\\[(\\d{1,2})]");

    private QuoteCitations() {
    }

    static String fix(String answer, List<Document> sources) {
        if (answer == null || sources.isEmpty()) {
            return answer;
        }
        Matcher quote = QUOTE.matcher(answer);
        StringBuilder out = new StringBuilder();
        while (quote.find()) {
            String quoted = normalize(quote.group(1));
            String citations = quote.group(2);
            String replacement = citations;
            if (!citedSourceHolds(citations, quoted, sources)) {
                for (int i = 0; i < sources.size(); i++) {
                    if (normalize(sources.get(i).getText()).contains(quoted)) {
                        replacement = "[" + (i + 1) + "]";
                        break;
                    }
                }
            }
            String whole = quote.group();
            String fixed = whole.substring(0, whole.length() - citations.length()) + replacement;
            quote.appendReplacement(out, Matcher.quoteReplacement(fixed));
        }
        quote.appendTail(out);
        return out.toString();
    }

    private static boolean citedSourceHolds(String citations, String quoted, List<Document> sources) {
        Matcher number = NUMBER.matcher(citations);
        while (number.find()) {
            int index = Integer.parseInt(number.group(1));
            if (index >= 1 && index <= sources.size() && normalize(sources.get(index - 1).getText()).contains(quoted)) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, one space between words, straight apostrophes, no trailing full stop. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFC).toLowerCase(Locale.ROOT)
                .replace('’', '\'').replace('‘', '\'')
                .replaceAll("\\s+", " ").strip().replaceAll("[.,;:]$", "");
    }
}
