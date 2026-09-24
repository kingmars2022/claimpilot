package com.claimpilot.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;

/**
 * Splits Markdown or plain text into one {@link Document} per heading, so each chunk stays inside
 * one topic and citations can name that topic ("Vacation", "Client meals").
 * <p>
 * The section name is the heading path below the document title, for example
 * "Travel › Mileage". Text before the first sub-heading is labelled with the title itself.
 * Headings inside fenced code blocks are ignored. Text without any heading becomes a single
 * section with no name.
 */
public final class MarkdownSections {

    public static final String META_SECTION = "section";

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final String SEPARATOR = " › ";

    private MarkdownSections() {
    }

    public static List<Document> split(String markdown) {
        List<Document> sections = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return sections;
        }

        String title = null;
        String[] path = new String[7];  // index = heading level
        String currentName = null;
        StringBuilder body = new StringBuilder();
        boolean inFence = false;

        for (String line : markdown.split("\\R", -1)) {
            if (line.strip().startsWith("```") || line.strip().startsWith("~~~")) {
                inFence = !inFence;
            }
            Matcher heading = inFence ? null : HEADING.matcher(line);
            if (heading != null && heading.matches()) {
                add(sections, currentName, body);
                body.setLength(0);

                int level = heading.group(1).length();
                String text = heading.group(2).strip();
                path[level] = text;
                for (int deeper = level + 1; deeper < path.length; deeper++) {
                    path[deeper] = null;
                }
                if (level == 1 && title == null) {
                    title = text;
                }
                currentName = sectionName(path, title);
            }
            body.append(line).append('\n');
        }
        add(sections, currentName, body);
        return sections;
    }

    /** Heading path from level 2 down; falls back to the title for text directly under it. */
    private static String sectionName(String[] path, String title) {
        List<String> parts = new ArrayList<>();
        for (int level = 2; level < path.length; level++) {
            if (path[level] != null) {
                parts.add(path[level]);
            }
        }
        return parts.isEmpty() ? title : String.join(SEPARATOR, parts);
    }

    /** Keeps a section only if it has text beyond its heading line. */
    private static void add(List<Document> sections, String name, StringBuilder body) {
        String text = body.toString().strip();
        String withoutHeadings = text.replaceAll("(?m)^#{1,6}\\s+.*$", "").strip();
        if (withoutHeadings.isEmpty()) {
            return;
        }
        sections.add(name == null ? new Document(text) : new Document(text, Map.of(META_SECTION, name)));
    }
}
