package com.claimpilot.conversation;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.claimpilot.common.NotFoundException;

@Service
public class ConversationService {

    private static final int HISTORY_LIST_SIZE = 50;

    private final ConversationRepository repository;

    public ConversationService(ConversationRepository repository) {
        this.repository = repository;
    }

    public List<ConversationSummary> list(String owner) {
        return repository.findByOwnerOrderByUpdatedAtDesc(owner, PageRequest.of(0, HISTORY_LIST_SIZE));
    }

    /**
     * Someone else's conversation is reported as missing rather than forbidden, so ids cannot be
     * probed.
     */
    public Conversation getOwned(String id, String owner) {
        return repository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new NotFoundException("Conversation " + id + " does not exist."));
    }

    public Conversation save(Conversation conversation) {
        return repository.save(conversation);
    }

    public void deleteAll(String owner) {
        repository.deleteByOwner(owner);
    }

    public void delete(String id, String owner) {
        repository.delete(getOwned(id, owner));
    }
}
