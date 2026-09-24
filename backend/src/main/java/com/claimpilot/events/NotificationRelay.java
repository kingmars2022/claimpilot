package com.claimpilot.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import com.claimpilot.audit.AuditAction;
import com.claimpilot.audit.AuditService;
import com.claimpilot.config.AppProperties;
import com.claimpilot.document.DocumentStatus;

/**
 * Routes "processing finished" to the user. Inline mode notifies directly. Kafka mode publishes the
 * event, because the worker that processed the upload may not be the server the user's browser is
 * connected to; every API instance consumes it (see {@link ProcessedEventsListener}).
 */
@Component
public class NotificationRelay {

    static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Logger log = LoggerFactory.getLogger(NotificationRelay.class);

    private final NotificationService notifications;
    private final AuditService audit;
    private final ObjectProvider<KafkaTemplate<String, String>> kafka;
    private final AppProperties properties;

    public NotificationRelay(NotificationService notifications, AuditService audit,
                             ObjectProvider<KafkaTemplate<String, String>> kafka, AppProperties properties) {
        this.notifications = notifications;
        this.audit = audit;
        this.kafka = kafka;
        this.properties = properties;
    }

    @EventListener
    public void on(DocumentProcessed event) {
        boolean ready = event.status() == DocumentStatus.READY;
        audit.record(event.ownerId(), ready ? AuditAction.DOCUMENT_READY : AuditAction.DOCUMENT_FAILED,
                event.kind().name(), event.documentId(),
                ready ? event.fileName() : event.fileName() + ": " + event.errorMessage());
        if (properties.events().kafka()) {
            kafka.getObject().send(properties.events().processedTopic(), event.ownerId().toString(),
                    JSON.writeValueAsString(event));
            log.debug("Published processed event for {}", event.documentId());
        } else {
            notifications.documentProcessed(event);
        }
    }
}
