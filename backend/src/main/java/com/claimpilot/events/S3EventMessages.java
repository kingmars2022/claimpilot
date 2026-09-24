package com.claimpilot.events;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * The message Amazon S3 sends to SQS when an object is created. Object keys arrive URL-encoded
 * (a space becomes "+"). When a notification is first configured, S3 also sends a "s3:TestEvent"
 * without records, which is simply acknowledged.
 */
final class S3EventMessages {

    private S3EventMessages() {
    }

    /** The keys of the created objects in the message; empty for test events and other messages. */
    static List<String> createdKeys(String body) {
        List<String> keys = new ArrayList<>();
        JsonNode root;
        try {
            root = NotificationRelay.JSON.readTree(body);
        } catch (RuntimeException ex) {
            return keys;
        }
        JsonNode records = root.get("Records");
        if (records == null || !records.isArray()) {
            return keys;
        }
        for (JsonNode record : records) {
            String event = record.path("eventName").asString("");
            String key = record.path("s3").path("object").path("key").asString("");
            if (event.startsWith("ObjectCreated") && !key.isEmpty()) {
                keys.add(URLDecoder.decode(key, StandardCharsets.UTF_8));
            }
        }
        return keys;
    }

    /** The same message S3 would send, for places where the storage cannot send it (locally). */
    static String objectCreated(String bucket, String key) {
        String encoded = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("%2F", "/");
        return NotificationRelay.JSON.writeValueAsString(Map.of("Records", List.of(Map.of(
                "eventSource", "aws:s3",
                "eventName", "ObjectCreated:Put",
                "s3", Map.of("bucket", Map.of("name", bucket), "object", Map.of("key", encoded))))));
    }
}
