package com.claimpilot.conversation;

import java.time.Instant;
import java.util.List;

import com.claimpilot.chat.AnswerStatus;
import com.claimpilot.chat.CallKit;
import com.claimpilot.chat.Citation;

/**
 * One turn in a conversation, stored inside the conversation document.
 *
 * @param status  for an answer: answered, unclear or not in the policy
 * @param callKit for an unclear or unanswered question: how to ask the insurer
 */
public record ChatMessage(
        Sender sender,
        String content,
        AnswerStatus status,
        List<Citation> citations,
        CallKit callKit,
        Instant createdAt) {

    public enum Sender {
        MEMBER,
        ASSISTANT
    }

    public static ChatMessage question(String text) {
        return new ChatMessage(Sender.MEMBER, text, null, List.of(), null, Instant.now());
    }

    public static ChatMessage answer(String text, AnswerStatus status, List<Citation> citations, CallKit callKit) {
        return new ChatMessage(Sender.ASSISTANT, text, status, citations, callKit, Instant.now());
    }
}
