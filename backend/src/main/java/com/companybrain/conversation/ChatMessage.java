package com.companybrain.conversation;

import java.time.Instant;
import java.util.List;

import com.companybrain.chat.Citation;

/**
 * One turn in a conversation, stored inside the conversation document.
 *
 * @param grounded for an answer: whether it cites the knowledge base
 */
public record ChatMessage(
        Sender sender,
        String content,
        Boolean grounded,
        List<Citation> citations,
        Instant createdAt) {

    public enum Sender {
        EMPLOYEE,
        ASSISTANT
    }

    public static ChatMessage question(String text) {
        return new ChatMessage(Sender.EMPLOYEE, text, null, List.of(), Instant.now());
    }

    public static ChatMessage answer(String text, boolean grounded, List<Citation> citations) {
        return new ChatMessage(Sender.ASSISTANT, text, grounded, citations, Instant.now());
    }
}
