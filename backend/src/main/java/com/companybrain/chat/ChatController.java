package com.companybrain.chat;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.companybrain.user.CurrentUserService;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final CurrentUserService currentUser;

    public ChatController(ChatService chatService, CurrentUserService currentUser) {
        this.chatService = chatService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ChatAnswer ask(@Valid @RequestBody ChatRequest request) {
        return chatService.ask(request, currentUser.get());
    }
}
