package com.claimpilot.events;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.claimpilot.document.DocumentDeleted;
import com.claimpilot.document.DocumentKind;
import com.claimpilot.document.DocumentStatus;
import com.claimpilot.user.AccountDeleted;

/**
 * Pushes "your document is ready" to the user's open browser tabs over Server-Sent Events, and keeps
 * the last few notifications for a tab that opens later.
 */
@Service
public class NotificationService {

    private static final Duration STREAM_TIMEOUT = Duration.ofMinutes(30);
    private static final int KEEP = 20;

    public record Notification(String type, UUID documentId, DocumentKind kind, String fileName, String message,
                               Instant at) {
    }

    private final Map<Long, List<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final Map<Long, Deque<Notification>> recent = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        List<SseEmitter> userStreams = streams.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>());
        userStreams.add(emitter);
        Runnable remove = () -> userStreams.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ex -> remove.run());
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException ex) {
            remove.run();
        }
        return emitter;
    }

    public void documentProcessed(DocumentProcessed event) {
        boolean ready = event.status() == DocumentStatus.READY;
        String message = ready
                ? event.fileName() + " is ready."
                : event.fileName() + " could not be read: " + event.errorMessage();
        publish(event.ownerId(), new Notification(ready ? "DOCUMENT_READY" : "DOCUMENT_FAILED", event.documentId(),
                event.kind(), event.fileName(), message, event.at()));
    }

    /** The account is gone: close its streams and forget its notifications (they name its files). */
    @EventListener
    public void onAccountDeleted(AccountDeleted event) {
        List<SseEmitter> open = streams.remove(event.userId());
        if (open != null) {
            open.forEach(SseEmitter::complete);
        }
        recent.remove(event.userId());
    }

    /** A deleted document no longer appears in the recent notifications. */
    @EventListener
    public void onDocumentDeleted(DocumentDeleted event) {
        Deque<Notification> list = recent.get(event.ownerId());
        if (list != null) {
            synchronized (list) {
                list.removeIf(n -> event.documentId().equals(n.documentId()));
            }
        }
    }

    public List<Notification> recent(Long userId) {
        Deque<Notification> list = recent.get(userId);
        if (list == null) {
            return List.of();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    void publish(Long userId, Notification notification) {
        Deque<Notification> list = recent.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (list) {
            list.addFirst(notification);
            while (list.size() > KEEP) {
                list.removeLast();
            }
        }
        for (SseEmitter emitter : streams.getOrDefault(userId, List.of())) {
            try {
                emitter.send(SseEmitter.event().name("document").data(notification));
            } catch (IOException | IllegalStateException ex) {
                streams.getOrDefault(userId, List.of()).remove(emitter);
            }
        }
    }
}
