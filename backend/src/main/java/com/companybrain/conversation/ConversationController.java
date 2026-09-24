package com.companybrain.conversation;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own conversation history. */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService service;

    public ConversationController(ConversationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConversationSummary> list(Authentication auth) {
        return service.list(auth.getName());
    }

    @GetMapping("/{id}")
    public Conversation get(@PathVariable String id, Authentication auth) {
        return service.getOwned(id, auth.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth) {
        service.delete(id, auth.getName());
        return ResponseEntity.noContent().build();
    }
}
