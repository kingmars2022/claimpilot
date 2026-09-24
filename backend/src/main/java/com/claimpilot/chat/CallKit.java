package com.claimpilot.chat;

import java.util.List;

/**
 * Everything needed to phone the insurer when the policy has no answer.
 *
 * @param details numbers and names to have ready, each read from the policy with its page
 * @param script  what to say, in English, ready to read aloud
 */
public record CallKit(
        String insurerName,
        Detail phone,
        Detail hours,
        List<Detail> details,
        String script) {

    /**
     * @param verified false when the value could not be matched to the policy text; the UI warns
     */
    public record Detail(String label, String value, Integer page, boolean verified) {
    }
}
