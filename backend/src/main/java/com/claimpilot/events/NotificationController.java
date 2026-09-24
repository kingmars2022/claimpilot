package com.claimpilot.events;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.claimpilot.security.TokenService;
import com.claimpilot.user.CurrentUserService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUserService currentUser;
    private final TokenService tokens;

    public NotificationController(NotificationService notifications, CurrentUserService currentUser,
                                  TokenService tokens) {
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.tokens = tokens;
    }

    /** A one-minute ticket to open the stream with: {@code /stream?access_token=<ticket>}. */
    @PostMapping("/ticket")
    public TokenService.IssuedToken ticket() {
        return tokens.issueStreamTicket(currentUser.get());
    }

    @GetMapping
    public List<NotificationService.Notification> recent() {
        return notifications.recent(currentUser.get().getId());
    }

    /**
     * Server-Sent Events. Browsers cannot add an Authorization header to an EventSource, so this one
     * endpoint takes a stream ticket as {@code ?access_token=} (see SecurityConfig).
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return notifications.subscribe(currentUser.get().getId());
    }
}
