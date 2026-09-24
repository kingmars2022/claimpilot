package com.claimpilot.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
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

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

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

    /**
     * Marks the account for deletion (it stops working at once), then deletes everything. Each step
     * can be repeated safely, so when one fails the job below finishes the deletion within minutes.
     *
     * @return true when everything is already gone, false when the rest will be finished by the job
     */
    public boolean deleteEverything(AppUser user) {
        user.requestDeletion();
        users.save(user);
        try {
            finish(user);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Deleting account {} was interrupted; it will be finished in the background", user.getId(), ex);
            return false;
        }
    }

    /** Finishes deletions that were interrupted (a database or storage outage, a restart). */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public void resumePendingDeletions() {
        for (AppUser user : users.findByDeletionRequestedAtIsNotNull()) {
            try {
                finish(user);
                log.info("Finished deleting account {}", user.getId());
            } catch (RuntimeException ex) {
                log.warn("Deleting account {} failed again; will retry", user.getId(), ex);
            }
        }
    }

    private void finish(AppUser user) {
        documents.deleteAll(user);
        conversations.deleteAll(user.getUsername());
        extractionLogs.deleteByOwnerId(user.getId());
        users.delete(user);
        events.publishEvent(new AccountDeleted(user.getId()));
    }
}
