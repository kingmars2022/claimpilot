package com.claimpilot.events;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka mode: every API instance reads every "processed" event (a consumer group per instance,
 * named after the instance, so a restart reuses its group instead of leaving an old one behind)
 * and pushes it to the browsers connected to it. Events from before this instance started are
 * skipped: nobody was connected to it then.
 */
@Component
@ConditionalOnProperty(name = EventsConfig.MODE, havingValue = "kafka")
public class ProcessedEventsListener {

    private final NotificationService notifications;
    private final Instant startedAt = Instant.now();

    public ProcessedEventsListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    @KafkaListener(topics = "${claimpilot.events.processed-topic}",
            groupId = "claimpilot-api-${claimpilot.events.instance-id}")
    public void onProcessed(String json) {
        DocumentProcessed event = NotificationRelay.JSON.readValue(json, DocumentProcessed.class);
        if (!event.at().isBefore(startedAt)) {
            notifications.documentProcessed(event);
        }
    }
}
