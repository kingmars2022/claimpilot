package com.claimpilot.user;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.claimpilot.conversation.ConversationService;
import com.claimpilot.document.DocumentService;
import com.claimpilot.extraction.ExtractionLogRepository;

/**
 * "Delete all my data", as Quebec's Law 25 expects for personal and health information. Files,
 * vectors, MongoDB records and every PostgreSQL row (profile, facts, claim drafts, by cascade)
 * are removed.
 */
@Service
public class AccountService {

    private final DocumentService documents;
    private final ConversationService conversations;
    private final ExtractionLogRepository extractionLogs;
    private final UserRepository users;
    private final ApplicationEventPublisher events;

    public AccountService(DocumentService documents, ConversationService conversations,
                          ExtractionLogRepository extractionLogs, UserRepository users,
                          ApplicationEventPublisher events) {
        this.events = events;
        this.documents = documents;
        this.conversations = conversations;
        this.extractionLogs = extractionLogs;
        this.users = users;
    }

    public void deleteEverything(AppUser user) {
        documents.deleteAll(user);
        conversations.deleteAll(user.getUsername());
        extractionLogs.deleteByOwnerId(user.getId());
        users.delete(user);
        events.publishEvent(new AccountDeleted(user.getId()));
    }
}
