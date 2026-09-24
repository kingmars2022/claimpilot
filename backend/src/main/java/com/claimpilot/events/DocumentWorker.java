package com.claimpilot.events;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.claimpilot.document.ProcessingService;

/**
 * Kafka mode: processes uploads published by any API instance. It plays the part an AWS Lambda
 * triggered by S3 would play in the cloud; the consumer group spreads uploads across workers.
 */
@Component
@ConditionalOnProperty(name = EventsConfig.MODE, havingValue = "kafka")
public class DocumentWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentWorker.class);

    private final ProcessingService processing;

    public DocumentWorker(ProcessingService processing) {
        this.processing = processing;
    }

    @KafkaListener(topics = "${claimpilot.events.uploaded-topic}", groupId = "claimpilot-workers")
    public void onUploaded(String documentId) {
        log.info("Worker picked up document {}", documentId);
        processing.process(UUID.fromString(documentId));
    }
}
