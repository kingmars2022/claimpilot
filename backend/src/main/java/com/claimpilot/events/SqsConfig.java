package com.claimpilot.events;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import com.claimpilot.config.AppProperties;
import com.claimpilot.document.DocumentRepository;

/**
 * "sqs" mode, the AWS design: the bucket notifies an SQS queue of each stored file and workers
 * consume the queue. Nothing is sent by the API unless {@code publish-uploads} stands in for S3.
 */
@Configuration
@ConditionalOnProperty(name = EventsConfig.MODE, havingValue = "sqs")
public class SqsConfig {

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(AppProperties properties) {
        AppProperties.Sqs settings = properties.events().sqs();
        SqsClientBuilder builder = SqsClient.builder().region(Region.of(settings.region()));
        if (settings.endpoint() != null && !settings.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(settings.endpoint()));
        }
        builder.credentialsProvider(settings.accessKey() == null || settings.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(settings.accessKey(), settings.secretKey())));
        SqsClient client = builder.build();
        if (settings.endpoint() != null && !settings.endpoint().isBlank()) {
            // Locally (ElasticMQ) the queue is created on start; on AWS it is created with the bucket.
            String name = settings.queueUrl().substring(settings.queueUrl().lastIndexOf('/') + 1);
            client.createQueue(b -> b.queueName(name));
        }
        return client;
    }

    @Bean
    ProcessingDispatcher sqsDispatcher(SqsClient sqs, DocumentRepository documents, AppProperties properties) {
        AppProperties.Sqs settings = properties.events().sqs();
        String bucket = properties.storage().s3() == null ? "local" : properties.storage().s3().bucket();
        return documentId -> {
            if (!settings.publishUploads()) {
                return;  // S3 itself notifies the queue
            }
            documents.findById(documentId).ifPresent(doc -> sqs.sendMessage(b -> b.queueUrl(settings.queueUrl())
                    .messageBody(S3EventMessages.objectCreated(bucket, doc.getStorageKey()))));
        };
    }
}
