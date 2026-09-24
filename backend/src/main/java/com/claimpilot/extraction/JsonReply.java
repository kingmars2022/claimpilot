package com.claimpilot.extraction;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the JSON object a model was asked to return. Local models sometimes wrap it in a code
 * fence or add a sentence around it, so only the outermost {...} is parsed.
 */
public final class JsonReply {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private JsonReply() {
    }

    /** The parsed object, or an empty object when the reply contains no valid JSON object. */
    public static JsonNode parse(String reply) {
        if (reply != null) {
            String text = reply.replaceAll("(?s)<think>.*?</think>", "");
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    JsonNode node = MAPPER.readTree(text.substring(start, end + 1));
                    if (node != null && node.isObject()) {
                        return node;
                    }
                } catch (RuntimeException ignored) {
                    // fall through to the empty object
                }
            }
        }
        return MAPPER.createObjectNode();
    }

    /** A string field, or null when missing, null or blank. */
    public static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        String text = value.isString() ? value.asString() : value.toString();
        return text.isBlank() || text.equalsIgnoreCase("null") ? null : text.strip();
    }

    public static String toJson(Object value) {
        return MAPPER.writeValueAsString(value);
    }
}
