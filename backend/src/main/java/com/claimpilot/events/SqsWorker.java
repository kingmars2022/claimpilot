package com.claimpilot.events;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

import com.claimpilot.config.AppProperties;
import com.claimpilot.document.DocumentRepository;
import com.claimpilot.document.ProcessingService;
import com.claimpilot.document.UploadedDocument;

/**
 * "sqs" mode: reads the bucket's "object created" notifications and processes each upload, the part
 * an S3-triggered Lambda would play. A message is deleted only after processing; otherwise SQS makes
 * it visible again and it is retried (for example when the notification arrives before the upload's
 * row is committed). After {@link #MAX_RECEIVES} attempts it is dropped with a warning; on AWS a
 * dead-letter queue keeps it.
 */
@Component
@ConditionalOnProperty(name = EventsConfig.MODE, havingValue = "sqs")
public class SqsWorker implements SmartLifecycle {

    static final int MAX_RECEIVES = 5;
    private static final Logger log = LoggerFactory.getLogger(SqsWorker.class);

    private final SqsClient sqs;
    private final ProcessingService processing;
    private final DocumentRepository documents;
    private final AppProperties.Sqs settings;
    private volatile boolean running;
    private Thread thread;

    public SqsWorker(SqsClient sqs, ProcessingService processing, DocumentRepository documents,
                     AppProperties properties) {
        this.sqs = sqs;
        this.processing = processing;
        this.documents = documents;
        this.settings = properties.events().sqs();
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofVirtual().name("sqs-worker").start(this::poll);
        log.info("Reading upload notifications from {}", settings.queueUrl());
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void poll() {
        while (running) {
            try {
                List<Message> messages = sqs.receiveMessage(b -> b.queueUrl(settings.queueUrl())
                        .maxNumberOfMessages(5)
                        .waitTimeSeconds(10)
                        .visibilityTimeout(settings.visibilityTimeoutSeconds())
                        .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT))
                        .messages();
                messages.forEach(this::handle);
            } catch (RuntimeException ex) {
                if (!running) {
                    return;
                }
                log.warn("Reading the upload queue failed; retrying", ex);
                sleep();
            }
        }
    }

    void handle(Message message) {
        List<String> keys = S3EventMessages.createdKeys(message.body());
        boolean allDone = true;
        for (String key : keys) {
            Optional<UploadedDocument> doc = documents.findByStorageKey(key);
            if (doc.isPresent()) {
                processing.process(doc.get().getId());
            } else {
                allDone = false;
            }
        }
        int receives = receiveCount(message);
        if (allDone || receives >= MAX_RECEIVES) {
            if (!allDone) {
                log.warn("No upload matches {} after {} attempts; dropping the notification", keys, receives);
            }
            sqs.deleteMessage(b -> b.queueUrl(settings.queueUrl()).receiptHandle(message.receiptHandle()));
        }
    }

    private static int receiveCount(Message message) {
        Map<MessageSystemAttributeName, String> attributes = message.attributes();
        try {
            return Integer.parseInt(attributes.getOrDefault(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1"));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
