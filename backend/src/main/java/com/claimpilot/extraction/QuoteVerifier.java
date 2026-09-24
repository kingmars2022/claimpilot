package com.claimpilot.extraction;

import java.util.List;
import java.util.Optional;

/**
 * Checks the model's work. The model says what a value is and quotes the text it came from; this
 * class confirms the quote really is in the document, finds its page, and confirms the value is
 * inside the quote. If the quote cannot be found (small models sometimes paraphrase), it falls
 * back to finding the line that contains the value itself.
 */
public final class QuoteVerifier {

    private QuoteVerifier() {
    }

    public static ExtractedFact verify(FactKey key, String value, String quote, List<PageText> pages) {
        if (quote != null && !quote.isBlank()) {
            Optional<PageText> page = findPage(quote, pages);
            if (page.isPresent() && Values.quoteSupports(key.type(), value, quote)) {
                return new ExtractedFact(key, value, quote.strip(), page.get().page(), true);
            }
        }
        // Fall back to the line that contains the value.
        for (PageText page : pages) {
            for (String line : page.text().split("\\R")) {
                if (!line.isBlank() && Values.quoteSupports(key.type(), value, line)) {
                    return new ExtractedFact(key, value, line.strip(), page.page(), true);
                }
            }
        }
        return new ExtractedFact(key, value, quote == null ? null : quote.strip(), null, false);
    }

    static Optional<PageText> findPage(String quote, List<PageText> pages) {
        String needle = Values.loose(quote);
        String needleAlnum = Values.alnum(quote);
        if (needleAlnum.isEmpty()) {
            return Optional.empty();
        }
        for (PageText page : pages) {
            if (Values.loose(page.text()).contains(needle) || Values.alnum(page.text()).contains(needleAlnum)) {
                return Optional.of(page);
            }
        }
        return Optional.empty();
    }
}
