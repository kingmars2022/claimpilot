package com.claimpilot.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;

import com.claimpilot.config.AppProperties;
import com.claimpilot.document.ProcessingService;

@Configuration
public class EventsConfig {

    static final String MODE = "claimpilot.events.mode";

    @Bean
    @ConditionalOnProperty(name = MODE, havingValue = "inline", matchIfMissing = true)
    ProcessingDispatcher inlineDispatcher(ProcessingService processing) {
        return processing::processAsync;
    }

    @Bean
    @ConditionalOnProperty(name = MODE, havingValue = "kafka")
    ProcessingDispatcher kafkaDispatcher(KafkaTemplate<String, String> kafka, AppProperties properties) {
        String topic = properties.events().uploadedTopic();
        return documentId -> kafka.send(topic, documentId.toString(), documentId.toString());
    }

    /** Three partitions, so up to three workers process uploads in parallel. */
    @Bean
    @ConditionalOnProperty(name = MODE, havingValue = "kafka")
    NewTopic uploadedTopic(AppProperties properties) {
        return TopicBuilder.name(properties.events().uploadedTopic()).partitions(3).replicas(1).build();
    }

    @Bean
    @ConditionalOnProperty(name = MODE, havingValue = "kafka")
    NewTopic processedTopic(AppProperties properties) {
        return TopicBuilder.name(properties.events().processedTopic()).partitions(1).replicas(1).build();
    }
}
