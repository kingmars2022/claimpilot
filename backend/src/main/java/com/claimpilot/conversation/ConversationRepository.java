package com.claimpilot.conversation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<ConversationSummary> findByOwnerOrderByUpdatedAtDesc(String owner, Pageable page);

    Optional<Conversation> findByIdAndOwner(String id, String owner);

    void deleteByOwner(String owner);
}
