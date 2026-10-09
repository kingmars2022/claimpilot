package com.claimpilot.search;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everyday words and the terms policies use for the same thing. A member asks about "a car I rent";
 * the policy says "vehicles of which the named insured is not owner". Neither vector nor keyword
 * search links the two reliably, so the common pairs are listed here.
 * <p>
 * Kept short and general on purpose: each entry is a wording difference found across policies, not a
 * fix for one question.
 */
public final class PolicyTerms {

    private static final Map<String, List<String>> POLICY_WORDS = Map.ofEntries(
            // Automobile
            Map.entry("car", List.of("vehicle", "automobile")),
            Map.entry("rent", List.of("owner", "non-owned", "replacement vehicle")),
            Map.entry("rental", List.of("owner", "non-owned", "replacement vehicle")),
            Map.entry("rented", List.of("owner", "non-owned", "replacement vehicle")),
            Map.entry("borrow", List.of("owner", "non-owned")),
            Map.entry("borrowed", List.of("owner", "non-owned")),
            Map.entry("crash", List.of("collision", "upset")),
            Map.entry("accident", List.of("collision", "loss")),
            Map.entry("stolen", List.of("theft")),
            Map.entry("steal", List.of("theft")),
            Map.entry("windscreen", List.of("windshield", "glass")),
            // Home
            Map.entry("house", List.of("dwelling", "residence")),
            Map.entry("home", List.of("dwelling", "residence")),
            Map.entry("apartment", List.of("dwelling", "residence")),
            Map.entry("condo", List.of("dwelling", "residence")),
            Map.entry("flood", List.of("water")),
            // Health and dental
            Map.entry("shot", List.of("vaccine", "immunization")),
            Map.entry("shots", List.of("vaccine", "immunization")),
            Map.entry("braces", List.of("orthodontic")),
            Map.entry("glasses", List.of("eyeglasses", "vision", "lenses")),
            Map.entry("contacts", List.of("contact lenses", "vision")),
            Map.entry("meds", List.of("drugs", "prescription")),
            Map.entry("medication", List.of("drugs", "prescription")),
            Map.entry("medicine", List.of("drugs", "prescription")),
            Map.entry("pills", List.of("drugs", "prescription")),
            Map.entry("physio", List.of("physiotherapy", "physiotherapist")),
            Map.entry("checkup", List.of("examination", "exam")),
            Map.entry("cleaning", List.of("scaling", "recall")));

    private PolicyTerms() {
    }

    /** The policy's words for an everyday word, or an empty list. */
    public static List<String> policyWords(String word) {
        return POLICY_WORDS.getOrDefault(word.toLowerCase(Locale.ROOT), List.of());
    }
}
