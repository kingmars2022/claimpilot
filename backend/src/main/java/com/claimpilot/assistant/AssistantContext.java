package com.claimpilot.assistant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.claimpilot.extraction.FactKey;

/**
 * What the planner may choose from: the member's ready policies and receipts (with a few key facts
 * so the model can tell them apart) and the name on their profile.
 */
public record AssistantContext(List<Doc> policies, List<Doc> receipts, String profileName) {

    public record Doc(UUID id, String fileName, Map<FactKey, String> facts) {

        String fact(FactKey key) {
            return facts.get(key);
        }

        /** The insurer's name when known, else the file name. */
        String label() {
            String insurer = fact(FactKey.INSURER_NAME);
            return insurer == null ? fileName : insurer + " (" + fileName + ")";
        }
    }
}
