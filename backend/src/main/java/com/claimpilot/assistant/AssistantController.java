package com.claimpilot.assistant;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.claimpilot.user.CurrentUserService;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistant;
    private final CurrentUserService currentUser;

    public AssistantController(AssistantService assistant, CurrentUserService currentUser) {
        this.assistant = assistant;
        this.currentUser = currentUser;
    }

    @PostMapping
    public AssistantDtos.Reply handle(@Valid @RequestBody AssistantDtos.Request request) {
        return assistant.handle(currentUser.get(), request);
    }
}
