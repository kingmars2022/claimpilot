package com.claimpilot.events;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.claimpilot.user.CurrentUserService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUserService currentUser;

    public NotificationController(NotificationService notifications, CurrentUserService currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<NotificationService.Notification> recent() {
        return notifications.recent(currentUser.get().getId());
    }

    /**
     * Server-Sent Events. Browsers cannot add an Authorization header to an EventSource, so this one
     * endpoint also accepts the token as {@code ?access_token=} (see SecurityConfig).
     */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return notifications.subscribe(currentUser.get().getId());
    }
}
