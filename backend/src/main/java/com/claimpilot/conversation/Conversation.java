package com.claimpilot.conversation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A chat thread about one policy, stored as one MongoDB document with its messages embedded: a
 * conversation is always read and written as a whole, and it has no fixed schema to migrate.
 */
@Document("conversations")
@CompoundIndex(name = "owner_updated", def = "{'owner': 1, 'updatedAt': -1}")
public class Conversation {

    private static final int TITLE_LENGTH = 80;

    @Id
    private String id;

    /** Username of the member who owns the conversation. */
    private String owner;

    /** The policy the questions are about. */
    private UUID policyId;

    private String title;

    private Instant createdAt;

    private Instant updatedAt;

    private List<ChatMessage> messages = new ArrayList<>();

    protected Conversation() {
        // for Spring Data
    }

    public static Conversation start(String owner, UUID policyId, String firstQuestion) {
        Conversation conversation = new Conversation();
        conversation.owner = owner;
        conversation.policyId = policyId;
        String flat = firstQuestion.strip().replaceAll("\\s+", " ");
        conversation.title = flat.length() <= TITLE_LENGTH ? flat : flat.substring(0, TITLE_LENGTH - 1) + "…";
        conversation.createdAt = Instant.now();
        conversation.updatedAt = conversation.createdAt;
        return conversation;
    }

    public void append(ChatMessage question, ChatMessage answer) {
        messages.add(question);
        messages.add(answer);
        updatedAt = answer.createdAt();
    }

    /** The last {@code turns} question/answer pairs, oldest first. */
    public List<ChatMessage> recentMessages(int turns) {
        int from = Math.max(0, messages.size() - turns * 2);
        return List.copyOf(messages.subList(from, messages.size()));
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public UUID getPolicyId() {
        return policyId;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }
}
