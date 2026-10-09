package com.claimpilot.eval;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;

/** How the accuracy evaluation scores one answer; kept apart so the normal build can test it. */
final class EvalScoring {

    private EvalScoring() {
    }

    /** {@code "page"} is one page number, or a list when the answer is on several pages. */
    static boolean pageMatches(JsonNode expected, List<Integer> cited) {
        if (expected.isArray()) {
            for (JsonNode page : expected) {
                if (cited.contains(page.asInt())) {
                    return true;
                }
            }
            return false;
        }
        return cited.contains(expected.asInt());
    }

    /** The answer on one line, shortened, for the report. */
    static String excerpt(String answer) {
        String flat = answer == null ? "" : answer.replaceAll("\\s+", " ").replace("|", "/").strip();
        return flat.length() <= 400 ? flat : flat.substring(0, 400) + "…";
    }

    static boolean contains(String text, JsonNode expected) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (JsonNode entry : expected) {
            boolean any = Stream.of(entry.asString().split("\\|"))
                    .anyMatch(alt -> lower.contains(alt.toLowerCase(Locale.ROOT)));
            if (!any) {
                return false;
            }
        }
        return true;
    }
}
